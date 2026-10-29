package com.hotel.loyalty.dto;

import com.hotel.loyalty.entity.RedemptionStatus;

import java.math.BigDecimal;

public record RedemptionResponse(
        Long redemptionId,
        long pointsRedeemed,
        BigDecimal discountAmount,
        long pointsBalance,
        RedemptionStatus status
) {
}
