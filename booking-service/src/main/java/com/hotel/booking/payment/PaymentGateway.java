package com.hotel.booking.payment;

import java.math.BigDecimal;

/**
 * Payment service provider abstraction. {@link MockPaymentGateway} is the default
 * ({@code payment.provider=mock}); {@link StripePaymentGateway} is used with {@code payment.provider=stripe}.
 */
public interface PaymentGateway {

    PaymentResult charge(PaymentCharge charge);

    RefundResult refund(String paymentReference, BigDecimal amount, String currency);
}
