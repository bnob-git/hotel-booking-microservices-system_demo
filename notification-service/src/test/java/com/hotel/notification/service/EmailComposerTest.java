package com.hotel.notification.service;

import com.hotel.notification.TestEvents;
import com.hotel.notification.config.EmailTemplateConfig;
import com.hotel.notification.dto.BookingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailComposerTest {

    private final EmailComposer composer =
            new EmailComposer(new EmailTemplateConfig().emailTemplateEngine(), "http://hotel.example/");

    @ParameterizedTest
    @CsvSource({
            "BOOKING_CONFIRMED, [Hotel] Booking HB7K2QXM confirmed",
            "BOOKING_CANCELLED, [Hotel] Booking HB7K2QXM cancelled",
            "BOOKING_CHECKED_IN, [Hotel] Booking HB7K2QXM checked in"
    })
    void shouldBuildSubjectPerEventType(String type, String expected) {
        assertEquals(expected, composer.compose(TestEvents.event(type, "jane@example.com")).subject());
    }

    @Test
    void shouldSupportOnlyNotificationEventTypes() {
        assertTrue(EmailComposer.supports("BOOKING_CONFIRMED"));
        assertTrue(EmailComposer.supports("BOOKING_CANCELLED"));
        assertTrue(EmailComposer.supports("BOOKING_CHECKED_IN"));
        assertFalse(EmailComposer.supports("BOOKING_CREATED"));
        assertFalse(EmailComposer.supports("SOMETHING_NEW"));
        assertFalse(EmailComposer.supports(null));
    }

    @Test
    void shouldRenderAllBookingDetailsInTextAndHtml() {
        BookingEmail email = composer.compose(TestEvents.event("BOOKING_CONFIRMED", "jane@example.com"));

        for (String body : new String[]{email.text(), email.html()}) {
            assertTrue(body.contains("Jane Guest"), body);
            assertTrue(body.contains("HB7K2QXM"), body);
            assertTrue(body.contains("Room 201"), body);
            assertTrue(body.contains("Tue 1 Dec 2026"), body);
            assertTrue(body.contains("Fri 4 Dec 2026"), body);
            assertTrue(body.contains("360.00 EUR"), body);
            assertTrue(body.contains("http://hotel.example/guest/lookup?code=HB7K2QXM"), body);
            assertTrue(body.contains("confirmed"), body);
        }
        assertTrue(email.text().contains("Guests:            2"));
        assertTrue(email.html().contains("href=\"http://hotel.example/guest/lookup?code=HB7K2QXM\""));
        assertFalse(email.text().contains("<"), "plain text body must not contain markup");
    }

    @Test
    void shouldEscapeUserSuppliedValuesInHtml() {
        BookingEvent base = TestEvents.event("BOOKING_CONFIRMED", "jane@example.com");
        BookingEvent event = new BookingEvent(1, base.eventId(), base.eventType(), base.occurredAt(),
                base.bookingId(), base.confirmationCode(), base.status(), null, "<script>alert(1)</script>",
                base.contactEmail(), base.roomId(), base.roomName(), base.checkInDate(), base.checkOutDate(),
                base.numGuests(), base.totalPrice(), base.currency());

        String html = composer.compose(event).html();

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void shouldFallBackForMissingOptionalFields() {
        BookingEvent event = new BookingEvent(1, java.util.UUID.randomUUID(), "BOOKING_CHECKED_IN", null,
                7L, "HBAAAAAA", null, 3L, null, "a@b.co", 9L, null, null, null, null, null, null);

        BookingEmail email = composer.compose(event);

        assertTrue(email.text().contains("Hello Guest"));
        assertTrue(email.text().contains("Room #9"));
        assertFalse(email.text().contains("Status:"));
    }
}
