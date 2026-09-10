package com.hotel.customer.dto;

public record CustomerResponse(
        String customerId,
        String name,
        String segment,
        String backend
) {
}
