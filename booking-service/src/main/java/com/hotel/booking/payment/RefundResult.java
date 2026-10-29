package com.hotel.booking.payment;

public record RefundResult(boolean succeeded, String reference, String message) {
}
