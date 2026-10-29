package com.hotel.booking.service;

import com.hotel.booking.client.LoyaltyClient;
import com.hotel.booking.dto.LoyaltyRedeemRequest;
import com.hotel.booking.dto.LoyaltyRedemption;
import com.hotel.booking.entity.Booking;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 2: redeems loyalty points against a booking's total at create time and gives them back
 * when the booking is cancelled or its hold expires.
 */
@Service
public class LoyaltyRedemptionService {

    static final long REDEMPTION_INCREMENT = 100;
    private static final Logger log = LoggerFactory.getLogger(LoyaltyRedemptionService.class);

    private final LoyaltyClient loyaltyClient;

    public LoyaltyRedemptionService(LoyaltyClient loyaltyClient) {
        this.loyaltyClient = loyaltyClient;
    }

    /**
     * Redeems {@code points} for {@code userId} and lowers the booking total by the discount (never below zero).
     * If the surrounding transaction rolls back, the redemption is reversed so points are not lost.
     */
    public void apply(Booking booking, Long userId, Long points) {
        if (points == null || points == 0) {
            return;
        }
        if (points < 0 || points % REDEMPTION_INCREMENT != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Points must be redeemed in positive multiples of " + REDEMPTION_INCREMENT);
        }
        if (booking.getTotalPrice() == null || booking.getTotalPrice().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to redeem points against");
        }

        LoyaltyRedemption redemption;
        try {
            redemption = loyaltyClient.redeem(new LoyaltyRedeemRequest(userId, points, booking.getConfirmationCode()));
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points could not be redeemed: insufficient balance or invalid amount");
            }
            log.error("Loyalty service unavailable while redeeming points for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Loyalty service unavailable, try again without points");
        }

        BigDecimal discount = redemption.discountAmount().min(booking.getTotalPrice());
        booking.setLoyaltyPointsRedeemed(redemption.pointsRedeemed());
        booking.setLoyaltyDiscount(discount);
        booking.setLoyaltyRedemptionId(redemption.redemptionId());
        booking.setTotalPrice(booking.getTotalPrice().subtract(discount));

        Long redemptionId = redemption.redemptionId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        reverseQuietly(redemptionId);
                    }
                }
            });
        }
    }

    /** Returns redeemed points to the guest once the booking change has committed. */
    public void reverseAfterCommit(Booking booking) {
        reverseAfterCommit(booking.getLoyaltyRedemptionId() == null ? List.of() : List.of(booking.getLoyaltyRedemptionId()));
    }

    public void reverseAfterCommit(List<Long> redemptionIds) {
        if (redemptionIds.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redemptionIds.forEach(LoyaltyRedemptionService.this::reverseQuietly);
                }
            });
        } else {
            redemptionIds.forEach(this::reverseQuietly);
        }
    }

    private void reverseQuietly(Long redemptionId) {
        try {
            loyaltyClient.reverse(redemptionId);
        } catch (Exception e) {
            log.error("Failed to reverse loyalty redemption {}", redemptionId, e);
        }
    }
}
