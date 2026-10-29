package com.hotel.booking.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StripePaymentGatewayTest {

    private MockRestServiceServer server;
    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new StripePaymentGateway(builder, "sk_test_123", "https://stripe.test");
    }

    private PaymentCharge charge(String token) {
        return new PaymentCharge(7L, "HBSTRIPE", token, new BigDecimal("360.00"), "EUR");
    }

    @Test
    void confirmsPaymentIntentInMinorUnits() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_123"))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "amount", "36000", "currency", "eur", "confirm", "true",
                        "payment_method_data[card][token]", "tok_visa")))
                .andRespond(withSuccess("{\"id\":\"pi_123\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        PaymentResult result = gateway.charge(charge("tok_visa"));

        assertTrue(result.approved());
        assertEquals("pi_123", result.reference());
        server.verify();
    }

    @Test
    void mapsCardErrorToDecline() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"card_declined\",\"decline_code\":\"generic_decline\",\"message\":\"Your card was declined.\"}}"));

        PaymentResult result = gateway.charge(charge("tok_chargeDeclined"));

        assertFalse(result.approved());
        assertEquals("generic_decline", result.declineCode());
        assertEquals("Your card was declined.", result.message());
    }

    @Test
    void refundsPartialAmount() {
        server.expect(requestTo("https://stripe.test/v1/refunds"))
                .andExpect(content().formDataContains(java.util.Map.of("payment_intent", "pi_123", "amount", "18000")))
                .andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

        RefundResult refund = gateway.refund("pi_123", new BigDecimal("180.00"), "EUR");

        assertTrue(refund.succeeded());
        assertEquals("re_1", refund.reference());
    }

    @Test
    void requiresSecretKey() {
        assertThrows(IllegalStateException.class,
                () -> new StripePaymentGateway(RestClient.builder(), " ", "https://stripe.test"));
        assertEquals(1999L, StripePaymentGateway.toMinorUnits(new BigDecimal("19.99")));
    }
}
