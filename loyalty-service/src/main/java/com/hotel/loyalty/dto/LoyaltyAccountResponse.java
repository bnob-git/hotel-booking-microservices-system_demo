package com.hotel.loyalty.dto;

import com.hotel.loyalty.entity.LoyaltyTier;

import java.math.BigDecimal;

public record LoyaltyAccountResponse(
        Long userId,
        String username,
        long pointsBalance,
        long lifetimePoints,
        LoyaltyTier tier,
        LoyaltyTier nextTier,
        long pointsToNextTier,
        BigDecimal pointValue
) {
}
