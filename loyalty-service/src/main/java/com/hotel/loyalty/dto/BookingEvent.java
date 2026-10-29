package com.hotel.loyalty.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local copy of the {@code booking-events} schema v1 (see docs/modernization/CONTRACTS.md).
 * {@code eventType} and {@code status} are plain strings so unknown values never fail deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingEvent(
        Integer schemaVersion,
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long bookingId,
        String confirmationCode,
        String status,
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

    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
}
