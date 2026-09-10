package com.hotel.account.dto;

public record AccountResponse(
        String accountId,
        String customerId,
        String type,
        String status,
        String backend
) {
}
