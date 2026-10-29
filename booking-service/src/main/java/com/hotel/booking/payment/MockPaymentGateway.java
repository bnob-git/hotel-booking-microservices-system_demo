package com.hotel.booking.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/** Deterministic in-process PSP using Stripe-style test tokens. */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    public static final String TOK_VISA = "tok_visa";
    public static final String TOK_MASTERCARD = "tok_mastercard";
    public static final String TOK_CHARGE_DECLINED = "tok_chargeDeclined";
    public static final String TOK_INSUFFICIENT_FUNDS = "tok_insufficientFunds";

    @Override
    public PaymentResult charge(PaymentCharge charge) {
        String method = charge.paymentMethod();
        if (TOK_VISA.equals(method) || TOK_MASTERCARD.equals(method)) {
            return PaymentResult.approved("mock_pi_" + UUID.randomUUID());
        }
        if (TOK_CHARGE_DECLINED.equals(method)) {
            return PaymentResult.declined("card_declined", "Your card was declined.");
        }
        if (TOK_INSUFFICIENT_FUNDS.equals(method)) {
            return PaymentResult.declined("insufficient_funds", "Your card has insufficient funds.");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method: " + method);
    }

    @Override
    public RefundResult refund(String paymentReference, BigDecimal amount, String currency) {
        return new RefundResult(true, "mock_re_" + UUID.randomUUID(), "Refund issued");
    }
}
