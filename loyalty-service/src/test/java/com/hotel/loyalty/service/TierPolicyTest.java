package com.hotel.loyalty.service;

import com.hotel.loyalty.config.LoyaltyProperties;
import com.hotel.loyalty.dto.TierResponse;
import com.hotel.loyalty.entity.LoyaltyTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TierPolicyTest {

    private final TierPolicy policy = new TierPolicy(LoyaltyProperties.defaults());

    @ParameterizedTest
    @CsvSource({
            "0, BRONZE",
            "999, BRONZE",
            "1000, SILVER",
            "4999, SILVER",
            "5000, GOLD",
            "14999, GOLD",
            "15000, PLATINUM",
            "1000000, PLATINUM"
    })
    void tierIsDerivedFromLifetimePointThresholds(long lifetime, LoyaltyTier expected) {
        assertEquals(expected, policy.tierFor(lifetime));
    }

    @ParameterizedTest
    @CsvSource({
            "360.00, BRONZE, 3600",
            "360.00, SILVER, 4500",
            "360.00, GOLD, 5400",
            "360.00, PLATINUM, 7200",
            "99.99, BRONZE, 999",
            "99.99, SILVER, 1249",   // 1249.875 floored
            "0.05, GOLD, 0",         // 0.75 floored
            "123.45, PLATINUM, 2469"
    })
    void earnIsFloorOfPriceTimesTenTimesMultiplier(String price, LoyaltyTier tier, long expected) {
        assertEquals(expected, policy.pointsEarned(new BigDecimal(price), tier));
    }

    @Test
    void nullOrNonPositiveTotalEarnsNothing() {
        assertEquals(0, policy.pointsEarned(null, LoyaltyTier.GOLD));
        assertEquals(0, policy.pointsEarned(BigDecimal.ZERO, LoyaltyTier.GOLD));
        assertEquals(0, policy.pointsEarned(new BigDecimal("-10"), LoyaltyTier.GOLD));
    }

    @Test
    void multipliersMatchContract() {
        assertEquals(0, new BigDecimal("1.0").compareTo(LoyaltyTier.BRONZE.getEarnMultiplier()));
        assertEquals(0, new BigDecimal("1.25").compareTo(LoyaltyTier.SILVER.getEarnMultiplier()));
        assertEquals(0, new BigDecimal("1.5").compareTo(LoyaltyTier.GOLD.getEarnMultiplier()));
        assertEquals(0, new BigDecimal("2.0").compareTo(LoyaltyTier.PLATINUM.getEarnMultiplier()));
    }

    @Test
    void nextTierAndPointsToNextTier() {
        assertEquals(LoyaltyTier.SILVER, policy.nextTier(LoyaltyTier.BRONZE));
        assertEquals(LoyaltyTier.GOLD, policy.nextTier(LoyaltyTier.SILVER));
        assertEquals(LoyaltyTier.PLATINUM, policy.nextTier(LoyaltyTier.GOLD));
        assertNull(policy.nextTier(LoyaltyTier.PLATINUM));

        assertEquals(1000, policy.pointsToNextTier(0));
        assertEquals(400, policy.pointsToNextTier(600));
        assertEquals(4000, policy.pointsToNextTier(1000));
        assertEquals(1, policy.pointsToNextTier(14_999));
        assertEquals(0, policy.pointsToNextTier(20_000));
    }

    @Test
    void tiersListIsOrderedWithPerks() {
        List<TierResponse> tiers = policy.tiers();

        assertEquals(List.of(LoyaltyTier.BRONZE, LoyaltyTier.SILVER, LoyaltyTier.GOLD, LoyaltyTier.PLATINUM),
                tiers.stream().map(TierResponse::tier).toList());
        assertEquals(List.of(0L, 1_000L, 5_000L, 15_000L),
                tiers.stream().map(TierResponse::minLifetimePoints).toList());
        tiers.forEach(t -> assertFalse(t.perks().isEmpty()));
    }

    @Test
    void earnRateIsConfigurable() {
        TierPolicy custom = new TierPolicy(new LoyaltyProperties(20, new BigDecimal("0.05"), 100));
        assertEquals(2000, custom.pointsEarned(new BigDecimal("100"), LoyaltyTier.BRONZE));
    }
}
