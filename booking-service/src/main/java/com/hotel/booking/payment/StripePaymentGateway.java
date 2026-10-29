package com.hotel.booking.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Stripe PaymentIntents (test mode) over the REST API. Enabled with {@code payment.provider=stripe}
 * and {@code STRIPE_SECRET_KEY}; test tokens such as {@code tok_visa} are confirmed immediately.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    public StripePaymentGateway(
            RestClient.Builder builder,
            @Value("${payment.stripe.secret-key}") String secretKey,
            @Value("${payment.stripe.base-url:https://api.stripe.com}") String baseUrl
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("payment.stripe.secret-key (STRIPE_SECRET_KEY) is required for payment.provider=stripe");
        }
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeaders(h -> h.setBearerAuth(secretKey))
                .build();
    }

    @Override
    public PaymentResult charge(PaymentCharge charge) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(toMinorUnits(charge.amount())));
        form.add("currency", charge.currency().toLowerCase(Locale.ROOT));
        form.add("confirm", "true");
        form.add("payment_method_data[type]", "card");
        form.add("payment_method_data[card][token]", charge.paymentMethod());
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");
        form.add("description", "Booking " + charge.confirmationCode());
        form.add("metadata[booking_id]", String.valueOf(charge.bookingId()));
        form.add("metadata[confirmation_code]", charge.confirmationCode());

        try {
            JsonNode intent = post("/v1/payment_intents", form);
            if ("succeeded".equals(intent.path("status").asText())) {
                return PaymentResult.approved(intent.path("id").asText());
            }
            return PaymentResult.declined(intent.path("status").asText(), "Payment was not completed");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.PAYMENT_REQUIRED.value()) {
                JsonNode error = e.getResponseBodyAs(JsonNode.class);
                JsonNode body = error == null ? null : error.path("error");
                String code = body == null ? "card_declined"
                        : body.path("decline_code").asText(body.path("code").asText("card_declined"));
                String message = body == null ? "Your card was declined." : body.path("message").asText("Your card was declined.");
                return PaymentResult.declined(code, message);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error", e);
        }
    }

    @Override
    public RefundResult refund(String paymentReference, BigDecimal amount, String currency) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentReference);
        form.add("amount", String.valueOf(toMinorUnits(amount)));
        try {
            JsonNode refund = post("/v1/refunds", form);
            return new RefundResult(true, refund.path("id").asText(), refund.path("status").asText());
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Refund failed at payment provider", e);
        }
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
    }

    static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
