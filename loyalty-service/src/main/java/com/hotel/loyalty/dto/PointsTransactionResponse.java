package com.hotel.loyalty.dto;

import com.hotel.loyalty.entity.TransactionType;

import java.time.LocalDateTime;

public record PointsTransactionResponse(
        Long id,
        TransactionType type,
        long points,
        Long bookingId,
        String confirmationCode,
        String description,
        LocalDateTime createdAt
) {
}
