package com.hotel.booking.dto;

public record LoyaltyRedeemRequest(Long userId, Long points, String bookingReference) {
}
