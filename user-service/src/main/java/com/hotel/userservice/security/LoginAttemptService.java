package com.hotel.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-username failed login tracking with a temporary lockout window.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockoutDuration;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
            @Value("${auth.lockout.duration-minutes:15}") long lockoutDurationMinutes) {

        this.maxAttempts = maxAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes);
    }

    public boolean isLocked(String username) {

        Attempts current = attempts.get(key(username));

        if (current == null) {
            return false;
        }

        if (Instant.now().isAfter(current.lockedUntil())) {
            attempts.remove(key(username), current);
            return false;
        }

        return current.count() >= maxAttempts;
    }

    public void recordFailure(String username) {

        attempts.compute(key(username), (k, current) -> {

            if (current == null || Instant.now().isAfter(current.lockedUntil())) {
                return new Attempts(1, Instant.now().plus(lockoutDuration));
            }

            return new Attempts(current.count() + 1, Instant.now().plus(lockoutDuration));
        });
    }

    public void reset(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.toLowerCase();
    }

    private record Attempts(int count, Instant lockedUntil) {
    }
}
