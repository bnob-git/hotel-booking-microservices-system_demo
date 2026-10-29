package com.hotel.notification;

import com.hotel.notification.dto.BookingEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TestEvents {

    private TestEvents() {
    }

    public static BookingEvent event(String type, String email) {
        return event(UUID.randomUUID(), type, email);
    }

    public static BookingEvent event(UUID eventId, String type, String email) {
        return new BookingEvent(
                1,
                eventId,
                type,
                Instant.parse("2026-10-29T12:00:00Z"),
                42L,
                "HB7K2QXM",
                "BOOKING_CANCELLED".equals(type) ? "CANCELLED" : "CONFIRMED",
                null,
                "Jane Guest",
                email,
                4L,
                "Room 201",
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 4),
                2,
                new BigDecimal("360.00"),
                "EUR"
        );
    }
}
