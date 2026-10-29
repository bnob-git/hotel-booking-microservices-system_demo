package com.hotel.loyalty.dto;

public record RedeemRequest(
        Long points,
        String bookingReference
) {
}
