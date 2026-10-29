package com.hotel.booking.dto;

import java.math.BigDecimal;

public record LoyaltyRedemption(
        Long redemptionId,
        long pointsRedeemed,
        BigDecimal discountAmount,
        long pointsBalance,
        String status
) {
}
