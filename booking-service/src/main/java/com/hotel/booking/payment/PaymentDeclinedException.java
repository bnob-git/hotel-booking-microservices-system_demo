package com.hotel.booking.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 402 Payment Required; the booking stays PENDING with payment status FAILED. */
public class PaymentDeclinedException extends ResponseStatusException {

    private final String declineCode;

    public PaymentDeclinedException(String declineCode, String message) {
        super(HttpStatus.PAYMENT_REQUIRED, message);
        this.declineCode = declineCode;
    }

    public String getDeclineCode() {
        return declineCode;
    }
}
