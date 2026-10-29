package com.hotel.loyalty.service;

import com.hotel.loyalty.dto.BookingEvent;
import com.hotel.loyalty.entity.LoyaltyAccount;
import com.hotel.loyalty.entity.LoyaltyTier;
import com.hotel.loyalty.entity.PointsTransaction;
import com.hotel.loyalty.entity.TransactionType;
import com.hotel.loyalty.repository.LoyaltyAccountRepository;
import com.hotel.loyalty.repository.PointsTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies {@code booking-events} to loyalty accounts. Idempotent on {@code eventId} and per booking.
 */
@Service
public class PointsAccrualService {

    private static final Logger log = LoggerFactory.getLogger(PointsAccrualService.class);

    public enum Outcome {
        EARNED,
        REVERSED,
        IGNORED,
        DUPLICATE
    }

    private final LoyaltyAccountRepository accountRepository;
    private final PointsTransactionRepository transactionRepository;
    private final TierPolicy tierPolicy;

    public PointsAccrualService(LoyaltyAccountRepository accountRepository,
                                PointsTransactionRepository transactionRepository,
                                TierPolicy tierPolicy) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.tierPolicy = tierPolicy;
    }

    @Transactional
    public Outcome handle(BookingEvent event) {
        if (event == null || event.eventType() == null || event.userId() == null || event.bookingId() == null) {
            return Outcome.IGNORED;
        }

        return switch (event.eventType()) {
            case BookingEvent.BOOKING_CONFIRMED -> earn(event);
            case BookingEvent.BOOKING_CANCELLED -> reverse(event);
            default -> Outcome.IGNORED;
        };
    }

    private Outcome earn(BookingEvent event) {
        if (isDuplicate(event)) {
            return Outcome.DUPLICATE;
        }
        // A booking earns at most once, even if CONFIRMED is re-emitted with a new eventId.
        if (transactionRepository.existsByBookingIdAndType(event.bookingId(), TransactionType.EARN)) {
            return Outcome.DUPLICATE;
        }

        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(event.userId())
                .orElseGet(() -> accountRepository.save(new LoyaltyAccount(event.userId(), event.username())));

        LoyaltyTier earningTier = account.getTier();
        long points = tierPolicy.pointsEarned(event.totalPrice(), earningTier);
        if (points <= 0) {
            return Outcome.IGNORED;
        }

        if (event.username() != null) {
            account.setUsername(event.username());
        }
        account.setPointsBalance(account.getPointsBalance() + points);
        account.setLifetimePoints(account.getLifetimePoints() + points);
        account.setTier(tierPolicy.tierFor(account.getLifetimePoints()));
        accountRepository.save(account);

        PointsTransaction tx = new PointsTransaction(account.getUserId(), TransactionType.EARN, points,
                "Earned for booking " + label(event) + " (€" + event.totalPrice().stripTrailingZeros().toPlainString()
                        + " × " + earningTier.getEarnMultiplier().stripTrailingZeros().toPlainString() + " "
                        + earningTier + " multiplier)");
        tx.setBookingId(event.bookingId());
        tx.setConfirmationCode(event.confirmationCode());
        tx.setSourceEventId(event.eventId());
        transactionRepository.save(tx);

        log.info("Earned {} points for user {} on booking {}", points, event.userId(), event.bookingId());
        return Outcome.EARNED;
    }

    private Outcome reverse(BookingEvent event) {
        if (isDuplicate(event)) {
            return Outcome.DUPLICATE;
        }
        if (transactionRepository.existsByBookingIdAndType(event.bookingId(), TransactionType.REVERSAL)) {
            return Outcome.DUPLICATE;
        }

        long earned = transactionRepository.sumPointsByBookingIdAndType(event.bookingId(), TransactionType.EARN);
        if (earned <= 0) {
            return Outcome.IGNORED;
        }

        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(event.userId()).orElse(null);
        if (account == null) {
            return Outcome.IGNORED;
        }

        account.setPointsBalance(account.getPointsBalance() - earned);
        account.setLifetimePoints(Math.max(0, account.getLifetimePoints() - earned));
        account.setTier(tierPolicy.tierFor(account.getLifetimePoints()));
        accountRepository.save(account);

        PointsTransaction tx = new PointsTransaction(account.getUserId(), TransactionType.REVERSAL, -earned,
                "Reversed: booking " + label(event) + " cancelled");
        tx.setBookingId(event.bookingId());
        tx.setConfirmationCode(event.confirmationCode());
        tx.setSourceEventId(event.eventId());
        transactionRepository.save(tx);

        log.info("Reversed {} points for user {} on cancelled booking {}", earned, event.userId(), event.bookingId());
        return Outcome.REVERSED;
    }

    private boolean isDuplicate(BookingEvent event) {
        return event.eventId() != null && transactionRepository.existsBySourceEventId(event.eventId());
    }

    private static String label(BookingEvent event) {
        return event.confirmationCode() != null ? event.confirmationCode() : "#" + event.bookingId();
    }
}
