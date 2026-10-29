package com.hotel.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.booking.dto.BookingEvent;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.UserResponse;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.kafka.BookingEventProducer;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.RoomRepository;
import com.hotel.booking.service.UserLookupService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack test (real SecurityConfig + JwtAuthFilter, Flyway on H2): guest endpoints work without a JWT
 * while the authenticated booking endpoints still require one.
 */
@SpringBootTest(properties = "booking.guest.rate-limit-per-minute=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestBookingSecurityIntegrationTest {

    private static final String JWT_SECRET = "test-only-jwt-secret-key-32-bytes-minimum";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private AuditEventProducer auditEventProducer;

    @MockBean
    private BookingEventProducer bookingEventProducer;

    @MockBean
    private UserLookupService userLookupService;

    private Room room;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        room = roomRepository.save(new Room("Room 201", "DOUBLE", BigDecimal.valueOf(120), true));
        clearInvocations(bookingEventProducer, auditEventProducer);
    }

    @Test
    void guestCanBookLookUpAndCancelWithoutJwt() throws Exception {
        String code = createGuestBooking("Jane@Example.com");

        Booking stored = bookingRepository.findByConfirmationCode(code).orElseThrow();
        assertNull(stored.getUserId());
        assertEquals("Jane Guest", stored.getGuestName());
        assertEquals(BookingStatus.CONFIRMED, stored.getStatus());

        ArgumentCaptor<BookingEvent> events = ArgumentCaptor.forClass(BookingEvent.class);
        verify(bookingEventProducer, times(2)).send(events.capture());
        assertEquals(List.of(BookingEventType.BOOKING_CREATED, BookingEventType.BOOKING_CONFIRMED),
                events.getAllValues().stream().map(BookingEvent::eventType).toList());
        BookingEvent confirmed = events.getAllValues().get(1);
        assertNull(confirmed.userId());
        assertEquals("Jane Guest", confirmed.username());
        assertEquals("Jane@Example.com", confirmed.contactEmail());
        assertEquals(code, confirmed.confirmationCode());

        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", code).param("email", "jane@example.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationCode").value(code))
                .andExpect(jsonPath("$.username").value("Jane Guest"))
                .andExpect(jsonPath("$.roomName").value("Room 201"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.userId").doesNotExist());

        mockMvc.perform(post("/api/bookings/guest/cancel").contentType(APPLICATION_JSON)
                        .content(json(Map.of("code", code, "email", "JANE@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Soft cancel: row kept, lookup still works and shows CANCELLED
        assertEquals(BookingStatus.CANCELLED, bookingRepository.findByConfirmationCode(code).orElseThrow().getStatus());
        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", code).param("email", "jane@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingEventProducer, times(3)).send(events.capture());
        assertEquals(BookingEventType.BOOKING_CANCELLED, events.getValue().eventType());

        // Cancelling twice is a conflict
        mockMvc.perform(post("/api/bookings/guest/cancel").contentType(APPLICATION_JSON)
                        .content(json(Map.of("code", code, "email", "jane@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    void lookupReturns404UnlessCodeAndEmailBothMatch() throws Exception {
        String code = createGuestBooking("jane@example.com");

        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", code).param("email", "other@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found"));
        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", "HBZZZZZZ").param("email", "jane@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found"));
        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", code))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings/guest/cancel").contentType(APPLICATION_JSON)
                        .content(json(Map.of("code", code, "email", "other@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestCreateValidatesInputLikeAuthenticatedCreate() throws Exception {
        Map<String, Object> invalidEmail = guestPayload("not-an-email");
        mockMvc.perform(post("/api/bookings/guest").contentType(APPLICATION_JSON).content(json(invalidEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Guest email is invalid"));

        Map<String, Object> badDates = guestPayload("jane@example.com");
        badDates.put("checkOutDate", badDates.get("checkInDate"));
        mockMvc.perform(post("/api/bookings/guest").contentType(APPLICATION_JSON).content(json(badDates)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Check-out date must be after check-in date"));

        Map<String, Object> tooManyGuests = guestPayload("jane@example.com");
        tooManyGuests.put("numGuests", 11);
        mockMvc.perform(post("/api/bookings/guest").contentType(APPLICATION_JSON).content(json(tooManyGuests)))
                .andExpect(status().isBadRequest());

        createGuestBooking("jane@example.com");
        mockMvc.perform(post("/api/bookings/guest").contentType(APPLICATION_JSON)
                        .content(json(guestPayload("bob@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Room already booked for these dates"));

        Map<String, Object> unknownRoom = guestPayload("jane@example.com");
        unknownRoom.put("roomId", 999_999);
        mockMvc.perform(post("/api/bookings/guest").contentType(APPLICATION_JSON).content(json(unknownRoom)))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherBookingEndpointsStillRequireJwt() throws Exception {
        mockMvc.perform(get("/api/bookings/my")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings/1")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/bookings/1")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings").contentType(APPLICATION_JSON)
                        .content(json(guestPayload("jane@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesGuestBookingWithGuestNameAsUsername() throws Exception {
        String code = createGuestBooking("jane@example.com");

        mockMvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token(1L, "admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].confirmationCode").value(code))
                .andExpect(jsonPath("$[0].username").value("Jane Guest"));
        verifyNoInteractions(userLookupService);
    }

    @Test
    void guestEndpointsIgnoreInvalidOrUserTokens() throws Exception {
        String code = createGuestBooking("jane@example.com");
        when(userLookupService.getUserById(3L)).thenReturn(alice());

        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", code).param("email", "jane@example.com")
                        .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/guest/lookup").param("code", code).param("email", "jane@example.com")
                        .header("Authorization", "Bearer " + token(3L, "alice", "USER")))
                .andExpect(status().isOk());

        // A regular user may not read a guest booking by id (not the owner)
        Long id = bookingRepository.findByConfirmationCode(code).orElseThrow().getId();
        mockMvc.perform(get("/api/bookings/" + id).header("Authorization", "Bearer " + token(3L, "alice", "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registeredUsersBookingCannotBeCancelledThroughGuestFlow() throws Exception {
        Booking booking = new Booking(LocalDate.now().plusDays(20), LocalDate.now().plusDays(22), room.getId(), 3L);
        booking.setContactEmail("alice@example.com");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);
        when(userLookupService.getUserById(3L)).thenReturn(alice());

        mockMvc.perform(get("/api/bookings/guest/lookup")
                        .param("code", booking.getConfirmationCode()).param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
        mockMvc.perform(post("/api/bookings/guest/cancel").contentType(APPLICATION_JSON)
                        .content(json(Map.of("code", booking.getConfirmationCode(), "email", "alice@example.com"))))
                .andExpect(status().isForbidden());
    }

    // ================= HELPERS =================

    private String createGuestBooking(String email) throws Exception {
        String body = mockMvc.perform(post("/api/bookings/guest").contentType(APPLICATION_JSON)
                        .content(json(guestPayload(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.username").value("Jane Guest"))
                .andExpect(jsonPath("$.numGuests").value(2))
                .andExpect(jsonPath("$.totalPrice").value(360.0))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        String code = node.get("confirmationCode").asText();
        assertTrue(code.matches("HB[A-Z0-9]{6}"), code);
        return code;
    }

    private Map<String, Object> guestPayload(String email) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("roomId", room.getId());
        payload.put("checkInDate", LocalDate.now().plusDays(10).toString());
        payload.put("checkOutDate", LocalDate.now().plusDays(13).toString());
        payload.put("numGuests", 2);
        payload.put("guestName", "Jane Guest");
        payload.put("guestEmail", email);
        payload.put("specialRequests", "Late arrival");
        return payload;
    }

    private static UserResponse alice() {
        UserResponse user = new UserResponse();
        user.setId(3L);
        user.setUsername("alice");
        return user;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String token(Long userId, String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .compact();
    }
}
