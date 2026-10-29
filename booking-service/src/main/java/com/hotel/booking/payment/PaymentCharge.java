package com.hotel.booking.payment;

import java.math.BigDecimal;

public record PaymentCharge(
        Long bookingId,
        String confirmationCode,
        String paymentMethod,
        BigDecimal amount,
        String currency
) {
}
