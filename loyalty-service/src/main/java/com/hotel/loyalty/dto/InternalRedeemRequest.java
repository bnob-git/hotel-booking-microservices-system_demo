package com.hotel.loyalty.dto;

public record InternalRedeemRequest(
        Long userId,
        Long points,
        String bookingReference
) {
}
