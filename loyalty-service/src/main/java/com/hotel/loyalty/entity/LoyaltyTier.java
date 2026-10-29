package com.hotel.loyalty.entity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tiers are derived from lifetime points (never decreased by redemptions).
 */
public enum LoyaltyTier {

    BRONZE(0, new BigDecimal("1.00"), List.of(
            "Earn 10 points per €1",
            "Member-only offers")),

    SILVER(1_000, new BigDecimal("1.25"), List.of(
            "1.25× points on every stay",
            "Late checkout (subject to availability)",
            "Priority email support")),

    GOLD(5_000, new BigDecimal("1.50"), List.of(
            "1.5× points on every stay",
            "Guaranteed late checkout until 14:00",
            "Free room upgrade (subject to availability)",
            "Welcome drink")),

    PLATINUM(15_000, new BigDecimal("2.00"), List.of(
            "2× points on every stay",
            "Guaranteed room upgrade",
            "Free breakfast",
            "Dedicated concierge line"));

    private final long minLifetimePoints;
    private final BigDecimal earnMultiplier;
    private final List<String> perks;

    LoyaltyTier(long minLifetimePoints, BigDecimal earnMultiplier, List<String> perks) {
        this.minLifetimePoints = minLifetimePoints;
        this.earnMultiplier = earnMultiplier;
        this.perks = perks;
    }

    public long getMinLifetimePoints() {
        return minLifetimePoints;
    }

    public BigDecimal getEarnMultiplier() {
        return earnMultiplier;
    }

    public List<String> getPerks() {
        return perks;
    }
}
