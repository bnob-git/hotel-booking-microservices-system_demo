package com.hotel.booking.service;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class BookingHoldPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private final BookingHoldPolicy policy =
            new BookingHoldPolicy(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));

    @Test
    void newHoldExpiresAfterTtl() {
        assertEquals(Instant.parse("2026-11-01T10:15:00Z"), policy.newHoldExpiry());
    }

    @Test
    void pendingBookingIsExpiredOnceHoldLapses() {
        Booking b = new Booking();
        b.setStatus(BookingStatus.PENDING);

        b.setHoldExpiresAt(NOW.plusSeconds(1));
        assertFalse(policy.isExpired(b));

        b.setHoldExpiresAt(NOW);
        assertTrue(policy.isExpired(b));

        b.setHoldExpiresAt(null);
        assertFalse(policy.isExpired(b));

        b.setHoldExpiresAt(NOW.minusSeconds(60));
        b.setStatus(BookingStatus.CONFIRMED);
        assertFalse(policy.isExpired(b));
    }
}
