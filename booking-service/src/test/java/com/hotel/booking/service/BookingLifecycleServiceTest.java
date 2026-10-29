package com.hotel.booking.service;

import com.hotel.booking.dto.AuditEventRequest;
import com.hotel.booking.dto.AuditEventType;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.PaymentStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.payment.MockPaymentGateway;
import com.hotel.booking.payment.PaymentDeclinedException;
import com.hotel.booking.payment.PaymentGateway;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.security.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    private BookingRepository repository;
    private BookingMapper mapper;
    private AuditEventProducer auditEventProducer;
    private BookingEventPublisher publisher;
    private PaymentGateway paymentGateway;
    private LoyaltyRedemptionService loyaltyRedemptionService;
    private BookingLifecycleService service;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = mock(BookingRepository.class);
        mapper = mock(BookingMapper.class);
        auditEventProducer = mock(AuditEventProducer.class);
        publisher = mock(BookingEventPublisher.class);
        paymentGateway = spy(new MockPaymentGateway());
        loyaltyRedemptionService = mock(LoyaltyRedemptionService.class);
        RoomService roomService = mock(RoomService.class);
        BookingStateMachine stateMachine = new BookingStateMachine();
        service = new BookingLifecycleService(repository, mapper, roomService, auditEventProducer, publisher,
                stateMachine, new BookingHoldPolicy(clock, Duration.ofMinutes(15)),
                new CancellationPolicy(clock, stateMachine), paymentGateway, clock,
                loyaltyRedemptionService);

        Room room = new Room("Room 201", "DOUBLE", new BigDecimal("120.00"), true);
        room.setId(3L);
        when(roomService.getRoomById(3L)).thenReturn(room);
        when(repository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(Booking.class))).thenAnswer(inv -> BookingResponse.of(inv.getArgument(0), "alice", "Room 201"));
        auth = new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(10L, "alice", List.of()), null, List.of());
    }

    private Booking booking(BookingStatus status, int daysAhead) {
        Booking b = new Booking(TODAY.plusDays(daysAhead), TODAY.plusDays(daysAhead + 3), 3L, 10L);
        b.setId(7L);
        b.setStatus(status);
        b.setTotalPrice(new BigDecimal("360.00"));
        b.setConfirmationCode("HBTEST23");
        b.setHoldExpiresAt(NOW.plusSeconds(600));
        if (status != BookingStatus.PENDING) {
            b.setPaymentStatus(PaymentStatus.PAID);
            b.setAmountPaid(new BigDecimal("360.00"));
            b.setPaymentReference("mock_pi_original");
        }
        when(repository.findById(7L)).thenReturn(Optional.of(b));
        return b;
    }

    private AuditEventType auditType() {
        ArgumentCaptor<AuditEventRequest> captor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(captor.capture());
        return captor.getValue().getEventType();
    }

    private static HttpStatus statusOf(Runnable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call::run);
        return HttpStatus.valueOf(ex.getStatusCode().value());
    }

    // ---------- pay ----------

    @Test
    void successfulPaymentConfirmsBookingAndEmitsConfirmedEvents() {
        Booking b = booking(BookingStatus.PENDING, 10);

        BookingResponse response = service.pay(7L, "tok_visa", auth);

        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
        assertEquals(PaymentStatus.PAID, b.getPaymentStatus());
        assertEquals(new BigDecimal("360.00"), b.getAmountPaid());
        assertTrue(b.getPaymentReference().startsWith("mock_pi_"));
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        verify(publisher).publish(BookingEventType.BOOKING_CONFIRMED, b, "alice", "Room 201");
        assertEquals(AuditEventType.BOOKING_CONFIRMED, auditType());
    }

    @Test
    void mastercardAlsoSucceeds() {
        Booking b = booking(BookingStatus.PENDING, 10);
        service.pay(7L, "tok_mastercard", auth);
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
    }

    @Test
    void declinedPaymentKeepsHoldPendingAndMarksFailed() {
        Booking b = booking(BookingStatus.PENDING, 10);

        PaymentDeclinedException ex = assertThrows(PaymentDeclinedException.class,
                () -> service.pay(7L, "tok_chargeDeclined", auth));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, ex.getStatusCode());
        assertEquals("card_declined", ex.getDeclineCode());
        assertEquals(BookingStatus.PENDING, b.getStatus());
        assertEquals(PaymentStatus.FAILED, b.getPaymentStatus());
        assertNull(b.getAmountPaid());
        verify(repository).save(b);
        verifyNoInteractions(publisher, auditEventProducer);
    }

    @Test
    void insufficientFundsIsDeclined() {
        Booking b = booking(BookingStatus.PENDING, 10);
        PaymentDeclinedException ex = assertThrows(PaymentDeclinedException.class,
                () -> service.pay(7L, "tok_insufficientFunds", auth));
        assertEquals("insufficient_funds", ex.getDeclineCode());
        assertEquals(PaymentStatus.FAILED, b.getPaymentStatus());
    }

    @Test
    void retryAfterDeclineCanSucceed() {
        Booking b = booking(BookingStatus.PENDING, 10);
        assertThrows(PaymentDeclinedException.class, () -> service.pay(7L, "tok_chargeDeclined", auth));
        service.pay(7L, "tok_visa", auth);
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
        assertEquals(PaymentStatus.PAID, b.getPaymentStatus());
    }

    @Test
    void payingLapsedHoldIsConflict() {
        Booking b = booking(BookingStatus.PENDING, 10);
        b.setHoldExpiresAt(NOW);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.pay(7L, "tok_visa", auth)));
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void payingExpiredCancelledOrConfirmedBookingIsConflict() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.EXPIRED, BookingStatus.CANCELLED, BookingStatus.CONFIRMED}) {
            booking(status, 10);
            assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.pay(7L, "tok_visa", auth)), status.name());
        }
        verify(paymentGateway, never()).charge(any());
    }

    @Test
    void paymentMethodIsRequired() {
        booking(BookingStatus.PENDING, 10);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.pay(7L, " ", auth)));
    }

    @Test
    void unknownBookingIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.pay(99L, "tok_visa", auth)));
    }

    // ---------- cancel ----------

    @Test
    void cancellingPendingHoldIsFreeAndSkipsRefund() {
        Booking b = booking(BookingStatus.PENDING, 1);

        service.cancel(7L, "  Changed plans  ", auth);

        assertEquals(BookingStatus.CANCELLED, b.getStatus());
        assertEquals(PaymentStatus.UNPAID, b.getPaymentStatus());
        assertEquals(new BigDecimal("0.00"), b.getRefundAmount());
        assertEquals(NOW, b.getCancelledAt());
        assertEquals("Changed plans", b.getCancellationReason());
        verify(paymentGateway, never()).refund(any(), any(), any());
        verify(repository, never()).delete(any());
        verify(publisher).publish(BookingEventType.BOOKING_CANCELLED, b, "alice", "Room 201");
        assertEquals(AuditEventType.BOOKING_CANCELLED, auditType());
    }

    @Test
    void cancellingWeekAheadRefundsInFull() {
        Booking b = booking(BookingStatus.CONFIRMED, 7);

        service.cancel(7L, null, auth);

        assertEquals(PaymentStatus.REFUNDED, b.getPaymentStatus());
        assertEquals(new BigDecimal("360.00"), b.getRefundAmount());
        assertNull(b.getCancellationReason());
        verify(paymentGateway).refund("mock_pi_original", new BigDecimal("360.00"), "EUR");
    }

    @Test
    void cancellingFewDaysAheadRefundsHalf() {
        Booking b = booking(BookingStatus.CONFIRMED, 3);

        service.cancel(7L, null, auth);

        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, b.getPaymentStatus());
        assertEquals(new BigDecimal("180.00"), b.getRefundAmount());
        verify(paymentGateway).refund("mock_pi_original", new BigDecimal("180.00"), "EUR");
    }

    @Test
    void cancellingLastMinuteRefundsNothing() {
        Booking b = booking(BookingStatus.CONFIRMED, 1);

        service.cancel(7L, null, auth);

        assertEquals(BookingStatus.CANCELLED, b.getStatus());
        assertEquals(PaymentStatus.PAID, b.getPaymentStatus());
        assertEquals(new BigDecimal("0.00"), b.getRefundAmount());
        verify(paymentGateway, never()).refund(any(), any(), any());
    }

    @Test
    void checkedInCompletedAndTerminalBookingsCannotBeCancelled() {
        for (BookingStatus status : new BookingStatus[]{
                BookingStatus.CHECKED_IN, BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED}) {
            booking(status, 10);
            assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.cancel(7L, null, auth)), status.name());
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void quoteDelegatesToPolicy() {
        booking(BookingStatus.CONFIRMED, 3);
        assertEquals(50, service.getCancellationQuote(7L).refundPercent());
    }

    // ---------- check-in / complete ----------

    @Test
    void checkInIsAllowedOnArrivalDay() {
        Booking b = booking(BookingStatus.CONFIRMED, 0);

        service.checkIn(7L, auth);

        assertEquals(BookingStatus.CHECKED_IN, b.getStatus());
        assertEquals(NOW, b.getCheckedInAt());
        verify(publisher).publish(BookingEventType.BOOKING_CHECKED_IN, b, "alice", "Room 201");
        assertEquals(AuditEventType.BOOKING_CHECKED_IN, auditType());
    }

    @Test
    void checkInIsAllowedAfterArrivalDay() {
        Booking b = booking(BookingStatus.CONFIRMED, -1);
        service.checkIn(7L, auth);
        assertEquals(BookingStatus.CHECKED_IN, b.getStatus());
    }

    @Test
    void checkInBeforeArrivalIsConflict() {
        booking(BookingStatus.CONFIRMED, 1);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.checkIn(7L, auth)));
        verifyNoInteractions(publisher);
    }

    @Test
    void checkInRequiresConfirmedBooking() {
        booking(BookingStatus.PENDING, 0);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.checkIn(7L, auth)));
    }

    @Test
    void completeMovesCheckedInToCompleted() {
        Booking b = booking(BookingStatus.CHECKED_IN, -2);

        service.complete(7L);

        assertEquals(BookingStatus.COMPLETED, b.getStatus());
        assertEquals(NOW, b.getCompletedAt());
        verifyNoInteractions(publisher);
    }

    @Test
    void completeRequiresCheckedIn() {
        booking(BookingStatus.CONFIRMED, 0);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.complete(7L)));
    }

    @Test
    void auditFailureDoesNotBreakTransition() {
        Booking b = booking(BookingStatus.PENDING, 10);
        doThrow(new RuntimeException("kafka down")).when(auditEventProducer).send(any());
        service.pay(7L, "tok_visa", auth);
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
    }

    // ---------- loyalty points (Phase 2) ----------

    @Test
    void cancellingReturnsRedeemedPoints() {
        Booking b = booking(BookingStatus.CONFIRMED, 7);
        b.setLoyaltyRedemptionId(55L);

        service.cancel(7L, null, auth);

        verify(loyaltyRedemptionService).reverseAfterCommit(b);
    }

    @Test
    void expiringHoldsReturnsRedeemedPoints() {
        when(repository.findRedemptionIdsOfLapsedHolds(NOW)).thenReturn(List.of(55L, 56L));
        when(repository.expireHolds(eq(NOW), any())).thenReturn(2);

        assertEquals(2, service.expireLapsedHolds());

        verify(loyaltyRedemptionService).reverseAfterCommit(List.of(55L, 56L));
    }
}
