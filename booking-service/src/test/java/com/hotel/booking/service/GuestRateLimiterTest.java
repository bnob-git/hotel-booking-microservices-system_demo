package com.hotel.booking.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GuestRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);

    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };

    @Test
    void shouldAllowUpToLimitThenReject() {
        GuestRateLimiter limiter = new GuestRateLimiter(3, clock);

        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> limiter.check("1.1.1.1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void shouldTrackClientsIndependently() {
        GuestRateLimiter limiter = new GuestRateLimiter(1, clock);

        limiter.check("1.1.1.1");

        assertDoesNotThrow(() -> limiter.check("2.2.2.2"));
        assertThrows(ResponseStatusException.class, () -> limiter.check("1.1.1.1"));
    }

    @Test
    void shouldResetAfterWindow() {
        GuestRateLimiter limiter = new GuestRateLimiter(1, clock);

        limiter.check("1.1.1.1");
        now.addAndGet(GuestRateLimiter.WINDOW_MILLIS);

        assertDoesNotThrow(() -> limiter.check("1.1.1.1"));
    }
}
