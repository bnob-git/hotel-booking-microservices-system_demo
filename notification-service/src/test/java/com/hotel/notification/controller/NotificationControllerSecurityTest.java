package com.hotel.notification.controller;

import com.hotel.notification.entity.NotificationLog;
import com.hotel.notification.entity.NotificationStatus;
import com.hotel.notification.repository.NotificationLogRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerSecurityTest {

    private static final String JWT_SECRET = "test-only-jwt-secret-key-32-bytes-minimum";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationLogRepository repository;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        NotificationLog log = new NotificationLog();
        log.setEventId(UUID.randomUUID());
        log.setBookingId(42L);
        log.setConfirmationCode("HB7K2QXM");
        log.setEventType("BOOKING_CONFIRMED");
        log.setRecipient("jane@example.com");
        log.setSubject("[Hotel] Booking HB7K2QXM confirmed");
        log.setStatus(NotificationStatus.SENT);
        repository.save(log);
    }

    @Test
    void shouldRejectRequestsWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer garbage"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListNotificationsForAdmin() throws Exception {
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].confirmationCode").value("HB7K2QXM"))
                .andExpect(jsonPath("$[0].eventType").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$[0].recipient").value("jane@example.com"))
                .andExpect(jsonPath("$[0].subject").value("[Hotel] Booking HB7K2QXM confirmed"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].sentAt").exists());

        mockMvc.perform(get("/api/notifications").param("bookingId", "999")
                        .header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static String token(String role) {
        return Jwts.builder()
                .setSubject("admin")
                .claim("userId", 1L)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .compact();
    }
}
