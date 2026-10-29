package com.hotel.booking.service;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** How long an unpaid PENDING booking holds the room ({@code booking.hold.ttl}). */
@Component
public class BookingHoldPolicy {

    private final Clock clock;
    private final Duration ttl;

    public BookingHoldPolicy(Clock clock, @Value("${booking.hold.ttl:PT15M}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public Instant newHoldExpiry() {
        return clock.instant().plus(ttl);
    }

    public boolean isExpired(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING
                && booking.getHoldExpiresAt() != null
                && !clock.instant().isBefore(booking.getHoldExpiresAt());
    }

    public Duration getTtl() {
        return ttl;
    }
}
