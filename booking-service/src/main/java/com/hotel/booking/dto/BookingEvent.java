package com.hotel.booking.dto;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Versioned domain event contract for the {@code booking-events} topic (key = bookingId).
 * Additive changes only; bump {@link #SCHEMA_VERSION} for breaking changes.
 */
public record BookingEvent(
        int schemaVersion,
        UUID eventId,
        BookingEventType eventType,
        Instant occurredAt,
        Long bookingId,
        String confirmationCode,
        BookingStatus status,
        Long userId,
        String username,
        String contactEmail,
        Long roomId,
        String roomName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer numGuests,
        BigDecimal totalPrice,
        String currency
) {

    public static final int SCHEMA_VERSION = 1;
    public static final String DEFAULT_CURRENCY = "EUR";

    public static BookingEvent of(BookingEventType type, Booking booking, String username, String roomName) {
        return new BookingEvent(
                SCHEMA_VERSION,
                UUID.randomUUID(),
                type,
                Instant.now(),
                booking.getId(),
                booking.getConfirmationCode(),
                booking.getStatus(),
                booking.getUserId(),
                username,
                booking.getContactEmail(),
                booking.getRoomId(),
                roomName,
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getNumGuests(),
                booking.getTotalPrice(),
                DEFAULT_CURRENCY
        );
    }
}
