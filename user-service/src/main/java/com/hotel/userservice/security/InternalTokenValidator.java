package com.hotel.userservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InternalTokenValidator {

    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenValidator(String expectedToken) {
        this.expectedToken = require(expectedToken);
    }

    public static String require(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "internal.service-token (INTERNAL_SERVICE_TOKEN) must be configured"
            );
        }

        return token;
    }

    public boolean matches(String providedToken) {

        if (providedToken == null) {
            return false;
        }

        return MessageDigest.isEqual(
                providedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
