package com.hotel.loyalty.dto;

import com.hotel.loyalty.entity.LoyaltyTier;

import java.math.BigDecimal;
import java.util.List;

public record TierResponse(
        LoyaltyTier tier,
        long minLifetimePoints,
        BigDecimal earnMultiplier,
        List<String> perks
) {
}
