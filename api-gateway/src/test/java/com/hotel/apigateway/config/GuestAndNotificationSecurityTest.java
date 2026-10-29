package com.hotel.apigateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Track D: guest checkout is public at the gateway, notifications are ADMIN-only,
 * and the rest of /api/bookings/** still requires a JWT. Downstream services point
 * at an unreachable port, so a request that passes security fails with a non-401/403 error.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "JWT_SECRET=" + GuestAndNotificationSecurityTest.SECRET,
                "AI_SERVICE_TOKEN=test-service-token",
                "BOOKING_SERVICE_URL=http://localhost:1",
                "NOTIFICATION_SERVICE_URL=http://localhost:1"
        }
)
@AutoConfigureWebTestClient
class GuestAndNotificationSecurityTest {

    static final String SECRET = "test-jwt-secret-at-least-32-characters-long";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void guestCreateIsPublic() {
        HttpStatus status = statusOf(webTestClient.post().uri("/api/bookings/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}"));

        assertPassedSecurity(status);
    }

    @Test
    void guestLookupIsPublic() {
        HttpStatus status = statusOf(webTestClient.get()
                .uri("/api/bookings/guest/lookup?code=HB7K2QXM&email=a@b.com"));

        assertPassedSecurity(status);
    }

    @Test
    void guestCancelIsPublic() {
        HttpStatus status = statusOf(webTestClient.post().uri("/api/bookings/guest/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"HB7K2QXM\",\"email\":\"a@b.com\"}"));

        assertPassedSecurity(status);
    }

    @Test
    void otherBookingEndpointsStillRequireJwt() {
        webTestClient.get().uri("/api/bookings/my").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/bookings/1").exchange().expectStatus().isUnauthorized();
        webTestClient.post().uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void notificationsRequireJwt() {
        webTestClient.get().uri("/api/notifications").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void notificationsForbiddenForNonAdmin() {
        webTestClient.get().uri("/api/notifications")
                .header("Authorization", "Bearer " + token("USER"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void notificationsAllowedForAdmin() {
        HttpStatus status = statusOf(webTestClient.get().uri("/api/notifications")
                .header("Authorization", "Bearer " + token("ADMIN")));

        assertPassedSecurity(status);
    }

    private HttpStatus statusOf(WebTestClient.RequestHeadersSpec<?> request) {
        return HttpStatus.valueOf(request.exchange().returnResult(String.class).getStatus().value());
    }

    private void assertPassedSecurity(HttpStatus status) {
        assertThat(status).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private String token(String role) {
        return Jwts.builder()
                .setSubject("someone")
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
