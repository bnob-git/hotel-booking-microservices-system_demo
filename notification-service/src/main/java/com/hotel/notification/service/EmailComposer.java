package com.hotel.notification.service;

import com.hotel.notification.dto.BookingEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Builds the subject and HTML + plain-text bodies for a booking notification. */
@Component
public class EmailComposer {

    /** Subject verb per supported event type: "[Hotel] Booking HB7K2QXM confirmed". */
    static final Map<String, String> ACTIONS = Map.of(
            "BOOKING_CONFIRMED", "confirmed",
            "BOOKING_CANCELLED", "cancelled",
            "BOOKING_CHECKED_IN", "checked in"
    );

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final TemplateEngine emailTemplateEngine;
    private final String frontendBaseUrl;

    public EmailComposer(TemplateEngine emailTemplateEngine,
                         @Value("${notification.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.emailTemplateEngine = emailTemplateEngine;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public static boolean supports(String eventType) {
        return eventType != null && ACTIONS.containsKey(eventType);
    }

    public static String subject(BookingEvent event) {
        return "[Hotel] Booking " + event.confirmationCode() + " " + ACTIONS.get(event.eventType());
    }

    public BookingEmail compose(BookingEvent event) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("action", ACTIONS.get(event.eventType()));
        context.setVariable("eventType", event.eventType());
        context.setVariable("guestName", orDefault(event.username(), "Guest"));
        context.setVariable("confirmationCode", event.confirmationCode());
        context.setVariable("roomName", orDefault(event.roomName(), "Room #" + event.roomId()));
        context.setVariable("checkIn", event.checkInDate() == null ? "-" : DATE_FORMAT.format(event.checkInDate()));
        context.setVariable("checkOut", event.checkOutDate() == null ? "-" : DATE_FORMAT.format(event.checkOutDate()));
        context.setVariable("numGuests", event.numGuests() == null ? "-" : event.numGuests().toString());
        context.setVariable("totalPrice", formatPrice(event.totalPrice(), event.currency()));
        context.setVariable("status", event.status());
        context.setVariable("lookupUrl", lookupUrl(event.confirmationCode()));

        return new BookingEmail(
                subject(event),
                emailTemplateEngine.process("booking.txt", context),
                emailTemplateEngine.process("booking.html", context)
        );
    }

    String lookupUrl(String confirmationCode) {
        return UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path("/guest/lookup")
                .queryParam("code", confirmationCode)
                .encode()
                .toUriString();
    }

    private static String formatPrice(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + orDefault(currency, "EUR");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
