package com.hotel.loyalty.service;

import com.hotel.loyalty.config.LoyaltyProperties;
import com.hotel.loyalty.dto.LoyaltyAccountResponse;
import com.hotel.loyalty.dto.PointsTransactionResponse;
import com.hotel.loyalty.dto.RedemptionResponse;
import com.hotel.loyalty.dto.TierResponse;
import com.hotel.loyalty.entity.LoyaltyAccount;
import com.hotel.loyalty.entity.LoyaltyTier;
import com.hotel.loyalty.entity.PointsTransaction;
import com.hotel.loyalty.entity.Redemption;
import com.hotel.loyalty.entity.RedemptionStatus;
import com.hotel.loyalty.entity.TransactionType;
import com.hotel.loyalty.repository.LoyaltyAccountRepository;
import com.hotel.loyalty.repository.PointsTransactionRepository;
import com.hotel.loyalty.repository.RedemptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class LoyaltyService {

    private final LoyaltyAccountRepository accountRepository;
    private final PointsTransactionRepository transactionRepository;
    private final RedemptionRepository redemptionRepository;
    private final TierPolicy tierPolicy;
    private final LoyaltyProperties properties;

    public LoyaltyService(LoyaltyAccountRepository accountRepository,
                          PointsTransactionRepository transactionRepository,
                          RedemptionRepository redemptionRepository,
                          TierPolicy tierPolicy,
                          LoyaltyProperties properties) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.redemptionRepository = redemptionRepository;
        this.tierPolicy = tierPolicy;
        this.properties = properties;
    }

    // Users without an account yet see an empty BRONZE account; nothing is persisted on read.
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getAccount(Long userId, String username) {
        LoyaltyAccount account = accountRepository.findByUserId(userId)
                .orElseGet(() -> new LoyaltyAccount(userId, username));

        LoyaltyTier tier = tierPolicy.tierFor(account.getLifetimePoints());

        return new LoyaltyAccountResponse(
                userId,
                account.getUsername() != null ? account.getUsername() : username,
                account.getPointsBalance(),
                account.getLifetimePoints(),
                tier,
                tierPolicy.nextTier(tier),
                tierPolicy.pointsToNextTier(account.getLifetimePoints()),
                properties.value()
        );
    }

    @Transactional(readOnly = true)
    public List<PointsTransactionResponse> getTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(t -> new PointsTransactionResponse(
                        t.getId(),
                        t.getType(),
                        t.getPoints(),
                        t.getBookingId(),
                        t.getConfirmationCode(),
                        t.getDescription(),
                        t.getCreatedAt()))
                .toList();
    }

    public List<TierResponse> getTiers() {
        return tierPolicy.tiers();
    }

    public BigDecimal discountFor(long points) {
        return properties.value()
                .multiply(BigDecimal.valueOf(points))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void validateRedemptionPoints(Long points) {
        int increment = properties.redemptionIncrement();

        if (points == null || points <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be a positive number");
        }
        if (points % increment != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Points must be redeemed in multiples of " + increment);
        }
    }

    @Transactional
    public RedemptionResponse redeem(Long userId, Long points, String bookingReference) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        validateRedemptionPoints(points);

        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(userId).orElse(null);
        long balance = account == null ? 0 : account.getPointsBalance();

        if (account == null || points > balance) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient points: balance is " + balance);
        }

        BigDecimal discount = discountFor(points);

        account.setPointsBalance(balance - points);
        accountRepository.save(account);

        Redemption redemption = redemptionRepository.save(
                new Redemption(userId, points, discount, blankToNull(bookingReference)));

        transactionRepository.save(new PointsTransaction(userId, TransactionType.REDEEM, -points,
                "Redeemed for €" + discount.toPlainString() + " discount"
                        + (redemption.getBookingReference() != null ? " (" + redemption.getBookingReference() + ")" : "")));

        return toResponse(redemption, account.getPointsBalance());
    }

    // Idempotent: reversing an already reversed redemption returns its current state.
    @Transactional
    public RedemptionResponse reverseRedemption(Long redemptionId) {
        Redemption redemption = redemptionRepository.findById(redemptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Redemption not found"));

        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(redemption.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loyalty account not found"));

        if (redemption.getStatus() == RedemptionStatus.REVERSED) {
            return toResponse(redemption, account.getPointsBalance());
        }

        redemption.markReversed();
        redemptionRepository.save(redemption);

        account.setPointsBalance(account.getPointsBalance() + redemption.getPoints());
        accountRepository.save(account);

        transactionRepository.save(new PointsTransaction(redemption.getUserId(), TransactionType.REVERSAL,
                redemption.getPoints(), "Redemption #" + redemption.getId() + " reversed"));

        return toResponse(redemption, account.getPointsBalance());
    }

    private static RedemptionResponse toResponse(Redemption redemption, long balance) {
        return new RedemptionResponse(
                redemption.getId(),
                redemption.getPoints(),
                redemption.getDiscountAmount(),
                balance,
                redemption.getStatus()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
