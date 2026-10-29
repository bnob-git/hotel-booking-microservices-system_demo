package com.hotel.loyalty.service;

import com.hotel.loyalty.config.LoyaltyProperties;
import com.hotel.loyalty.dto.TierResponse;
import com.hotel.loyalty.entity.LoyaltyTier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * Pure tier / earn-rate rules. Tiers are ordered by {@link LoyaltyTier#getMinLifetimePoints()}.
 */
@Component
public class TierPolicy {

    private final LoyaltyProperties properties;

    public TierPolicy(LoyaltyProperties properties) {
        this.properties = properties;
    }

    public LoyaltyTier tierFor(long lifetimePoints) {
        LoyaltyTier result = LoyaltyTier.BRONZE;
        for (LoyaltyTier tier : LoyaltyTier.values()) {
            if (lifetimePoints >= tier.getMinLifetimePoints()) {
                result = tier;
            }
        }
        return result;
    }

    public LoyaltyTier nextTier(LoyaltyTier tier) {
        LoyaltyTier[] tiers = LoyaltyTier.values();
        int next = tier.ordinal() + 1;
        return next < tiers.length ? tiers[next] : null;
    }

    public long pointsToNextTier(long lifetimePoints) {
        LoyaltyTier next = nextTier(tierFor(lifetimePoints));
        return next == null ? 0 : Math.max(0, next.getMinLifetimePoints() - lifetimePoints);
    }

    /**
     * floor(totalPrice × pointsPerEuro × tierMultiplier); 0 for null / non-positive totals.
     */
    public long pointsEarned(BigDecimal totalPrice, LoyaltyTier tier) {
        if (totalPrice == null || totalPrice.signum() <= 0) {
            return 0;
        }
        return totalPrice
                .multiply(BigDecimal.valueOf(properties.perEuro()))
                .multiply(tier.getEarnMultiplier())
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }

    public List<TierResponse> tiers() {
        return Arrays.stream(LoyaltyTier.values())
                .map(t -> new TierResponse(t, t.getMinLifetimePoints(), t.getEarnMultiplier(), t.getPerks()))
                .toList();
    }
}
