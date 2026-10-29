package com.hotel.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.booking.dto.BookingEvent;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.UserResponse;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.PaymentStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.kafka.BookingEventProducer;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.RoomRepository;
import com.hotel.booking.security.CustomUserPrincipal;
import com.hotel.booking.service.UserLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end Track B flow through the real controllers, method security, Flyway (H2) schema and
 * after-commit event publishing. Kafka producers and the user-service client are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingLifecycleIntegrationTest {

    private static final long OWNER_ID = 501L;
    private static final long OTHER_ID = 502L;
    private static final long ADMIN_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;

    @MockBean private AuditEventProducer auditEventProducer;
    @MockBean private BookingEventProducer bookingEventProducer;
    @MockBean private UserLookupService userLookupService;

    private Long roomId;

    @BeforeEach
    void setUp() {
        Room room = roomRepository.save(new Room("Track B Suite " + System.nanoTime(), "SUITE", new BigDecimal("100.00"), true));
        roomId = room.getId();
        when(userLookupService.getUserById(anyLong())).thenAnswer(inv -> {
            UserResponse user = new UserResponse();
            user.setId(inv.getArgument(0));
            user.setUsername("user" + inv.getArgument(0));
            return user;
        });
    }

    private static RequestPostProcessor as(long id, String role) {
        CustomUserPrincipal principal = new CustomUserPrincipal(id, "user" + id,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static RequestPostProcessor owner() {
        return as(OWNER_ID, "USER");
    }

    private static RequestPostProcessor other() {
        return as(OTHER_ID, "USER");
    }

    private static RequestPostProcessor admin() {
        return as(ADMIN_ID, "ADMIN");
    }

    private Long createPendingBooking(int daysAhead) throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(daysAhead);
        String body = objectMapper.writeValueAsString(Map.of(
                "roomId", roomId,
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkIn.plusDays(2).toString(),
                "numGuests", 2,
                "contactEmail", "guest@example.com"));
        String json = mockMvc.perform(post("/api/bookings").with(owner()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.holdExpiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private String pay(String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of("paymentMethod", token));
    }

    private List<BookingEventType> publishedEventTypes(Long bookingId) {
        ArgumentCaptor<BookingEvent> captor = ArgumentCaptor.forClass(BookingEvent.class);
        verify(bookingEventProducer, atLeast(0)).send(captor.capture());
        return captor.getAllValues().stream()
                .filter(e -> e.bookingId().equals(bookingId))
                .map(BookingEvent::eventType)
                .toList();
    }

    @Test
    void ownerPaysAfterDeclineAndCancelsWithFullRefund() throws Exception {
        Long id = createPendingBooking(30);
        assertEquals(List.of(BookingEventType.BOOKING_CREATED), publishedEventTypes(id));

        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(owner()).contentType(APPLICATION_JSON).content(pay("tok_chargeDeclined")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value("Your card was declined."));
        Booking declined = bookingRepository.findById(id).orElseThrow();
        assertEquals(BookingStatus.PENDING, declined.getStatus());
        assertEquals(PaymentStatus.FAILED, declined.getPaymentStatus());

        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(owner()).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(200.00))
                .andExpect(jsonPath("$.paymentReference").value(org.hamcrest.Matchers.startsWith("mock_pi_")));

        mockMvc.perform(get("/api/bookings/{id}/cancellation-quote", id).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.refundPercent").value(100))
                .andExpect(jsonPath("$.refundAmount").value(200.00));

        mockMvc.perform(post("/api/bookings/{id}/cancel", id).with(owner()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Plans changed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.refundAmount").value(200.00))
                .andExpect(jsonPath("$.cancellationReason").value("Plans changed"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        assertTrue(bookingRepository.existsById(id));
        assertEquals(List.of(BookingEventType.BOOKING_CREATED, BookingEventType.BOOKING_CONFIRMED,
                BookingEventType.BOOKING_CANCELLED), publishedEventTypes(id));
        verify(auditEventProducer, times(3)).send(any());

        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(owner()).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().isConflict());
    }

    @Test
    void otherUsersCannotPayQuoteOrCancel() throws Exception {
        Long id = createPendingBooking(20);

        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(other()).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings/{id}/cancellation-quote", id).with(other()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/cancel", id).with(other()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/bookings/{id}", id).with(other()))
                .andExpect(status().isForbidden());

        assertEquals(BookingStatus.PENDING, bookingRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        Long id = createPendingBooking(21);
        mockMvc.perform(post("/api/bookings/{id}/pay", id).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().is4xxClientError());
        assertEquals(BookingStatus.PENDING, bookingRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void adminCanPayAndCancelAnyBooking() throws Exception {
        Long id = createPendingBooking(4);

        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(admin()).contentType(APPLICATION_JSON).content(pay("tok_mastercard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/bookings/{id}/cancel", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refundAmount").value(100.00));
    }

    @Test
    void deleteIsSoftCancelAlias() throws Exception {
        Long id = createPendingBooking(15);

        mockMvc.perform(delete("/api/bookings/{id}", id).with(owner()))
                .andExpect(status().is2xxSuccessful());

        Booking cancelled = bookingRepository.findById(id).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());
        assertEquals(0, BigDecimal.ZERO.compareTo(cancelled.getRefundAmount()));
        assertTrue(publishedEventTypes(id).contains(BookingEventType.BOOKING_CANCELLED));
    }

    @Test
    void onlyAdminsCanCheckInAndCompleteOnArrivalDay() throws Exception {
        Long id = createPendingBooking(0);
        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(owner()).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/{id}/check-in", id).with(owner()))
                .andExpect(status().isForbidden());

        String json = mockMvc.perform(post("/api/bookings/{id}/check-in", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andReturn().getResponse().getContentAsString();
        JsonNode checkedIn = objectMapper.readTree(json);
        assertEquals("CHECKED_IN", checkedIn.get("status").asText());

        mockMvc.perform(post("/api/bookings/{id}/cancel", id).with(owner()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/bookings/{id}/complete", id).with(owner()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/complete", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Booking completed = bookingRepository.findById(id).orElseThrow();
        assertNotNull(completed.getCheckedInAt());
        assertNotNull(completed.getCompletedAt());
        assertEquals(List.of(BookingEventType.BOOKING_CREATED, BookingEventType.BOOKING_CONFIRMED,
                BookingEventType.BOOKING_CHECKED_IN), publishedEventTypes(id));
    }

    @Test
    void checkInBeforeArrivalIsConflict() throws Exception {
        Long id = createPendingBooking(5);
        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(owner()).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/{id}/check-in", id).with(admin()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/bookings/{id}/complete", id).with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredHoldCannotBePaid() throws Exception {
        Long id = createPendingBooking(25);
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setHoldExpiresAt(java.time.Instant.now().minusSeconds(5));
        bookingRepository.save(booking);

        mockMvc.perform(post("/api/bookings/{id}/pay", id).with(owner()).contentType(APPLICATION_JSON).content(pay("tok_visa")))
                .andExpect(status().isConflict());
    }
}
