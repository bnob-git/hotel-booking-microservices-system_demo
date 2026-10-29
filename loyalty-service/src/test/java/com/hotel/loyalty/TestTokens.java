package com.hotel.loyalty;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.util.Date;

public final class TestTokens {

    public static final String SECRET = "test-only-jwt-secret-key-32-bytes-minimum";

    private TestTokens() {
    }

    public static String bearer(long userId, String username, String role) {
        return "Bearer " + token(userId, username, role, SECRET, 60_000);
    }

    public static String token(long userId, String username, String role, String secret, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }
}
