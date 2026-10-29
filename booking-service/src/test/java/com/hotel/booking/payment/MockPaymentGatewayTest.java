package com.hotel.booking.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    private PaymentCharge charge(String token) {
        return new PaymentCharge(1L, "HBTEST01", token, new BigDecimal("100.00"), "EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tok_visa", "tok_mastercard"})
    void approvesSuccessTokensWithMockReference(String token) {
        PaymentResult result = gateway.charge(charge(token));
        assertTrue(result.approved());
        assertTrue(result.reference().matches("mock_pi_[0-9a-f-]{36}"));
    }

    @ParameterizedTest
    @CsvSource({"tok_chargeDeclined, card_declined", "tok_insufficientFunds, insufficient_funds"})
    void declinesFailureTokens(String token, String code) {
        PaymentResult result = gateway.charge(charge(token));
        assertFalse(result.approved());
        assertNull(result.reference());
        assertEquals(code, result.declineCode());
    }

    @Test
    void rejectsUnknownTokens() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gateway.charge(charge("tok_unknown")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void refundsAlwaysSucceed() {
        RefundResult refund = gateway.refund("mock_pi_1", new BigDecimal("50.00"), "EUR");
        assertTrue(refund.succeeded());
        assertTrue(refund.reference().startsWith("mock_re_"));
    }
}
