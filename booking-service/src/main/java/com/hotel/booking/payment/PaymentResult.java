package com.hotel.booking.payment;

public record PaymentResult(boolean approved, String reference, String declineCode, String message) {

    public static PaymentResult approved(String reference) {
        return new PaymentResult(true, reference, null, "Payment approved");
    }

    public static PaymentResult declined(String declineCode, String message) {
        return new PaymentResult(false, null, declineCode, message);
    }
}
