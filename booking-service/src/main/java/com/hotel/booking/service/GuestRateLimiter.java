package com.hotel.booking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window rate limiter for the public guest lookup/cancel endpoints,
 * keyed by client IP. Good enough for a single instance; swap for a shared store when scaling out.
 */
@Component
public class GuestRateLimiter {

    static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxRequestsPerWindow;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public GuestRateLimiter(@Value("${booking.guest.rate-limit-per-minute:20}") int maxRequestsPerWindow) {
        this(maxRequestsPerWindow, Clock.systemUTC());
    }

    GuestRateLimiter(int maxRequestsPerWindow, Clock clock) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clock = clock;
    }

    public void check(String clientKey) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MILLIS);
        }
        Window window = windows.compute(clientKey, (key, current) ->
                current == null || now - current.start >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(current.start, current.count + 1));
        if (window.count > maxRequestsPerWindow) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests, please try again later"
            );
        }
    }

    private record Window(long start, int count) {
    }
}
