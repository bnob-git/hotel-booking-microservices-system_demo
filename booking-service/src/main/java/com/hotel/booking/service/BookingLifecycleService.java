package com.hotel.booking.service;

import com.hotel.booking.dto.AuditEventRequest;
import com.hotel.booking.dto.AuditEventType;
import com.hotel.booking.dto.BookingEvent;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.CancellationQuote;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.PaymentStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.payment.PaymentCharge;
import com.hotel.booking.payment.PaymentDeclinedException;
import com.hotel.booking.payment.PaymentGateway;
import com.hotel.booking.payment.PaymentResult;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.security.CustomUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Track B lifecycle: payment (PENDING → CONFIRMED), soft cancel with refund policy,
 * admin check-in/complete and bulk hold expiry.
 */
@Service
public class BookingLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycleService.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final RoomService roomService;
    private final AuditEventProducer auditEventProducer;
    private final BookingEventPublisher bookingEventPublisher;
    private final BookingStateMachine stateMachine;
    private final BookingHoldPolicy holdPolicy;
    private final CancellationPolicy cancellationPolicy;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public BookingLifecycleService(
            BookingRepository bookingRepository,
            BookingMapper bookingMapper,
            RoomService roomService,
            AuditEventProducer auditEventProducer,
            BookingEventPublisher bookingEventPublisher,
            BookingStateMachine stateMachine,
            BookingHoldPolicy holdPolicy,
            CancellationPolicy cancellationPolicy,
            PaymentGateway paymentGateway,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.roomService = roomService;
        this.auditEventProducer = auditEventProducer;
        this.bookingEventPublisher = bookingEventPublisher;
        this.stateMachine = stateMachine;
        this.holdPolicy = holdPolicy;
        this.cancellationPolicy = cancellationPolicy;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    // ================= PAY =================

    @PreAuthorize("hasRole('ADMIN') or @bookingService.isOwner(#id, authentication.principal.id)")
    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public BookingResponse pay(Long id, String paymentMethod, Authentication authentication) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentMethod is required");
        }

        Booking booking = findBooking(id);
        stateMachine.assertTransition(booking.getStatus(), BookingStatus.CONFIRMED);
        if (holdPolicy.isExpired(booking)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking hold has expired");
        }

        BigDecimal amount = booking.getTotalPrice() == null ? BigDecimal.ZERO : booking.getTotalPrice();
        PaymentResult result = paymentGateway.charge(new PaymentCharge(
                booking.getId(),
                booking.getConfirmationCode(),
                paymentMethod.trim(),
                amount,
                BookingEvent.DEFAULT_CURRENCY
        ));

        if (!result.approved()) {
            booking.setPaymentStatus(PaymentStatus.FAILED);
            bookingRepository.save(booking);
            log.info("Payment declined for booking {} ({})", booking.getId(), result.declineCode());
            throw new PaymentDeclinedException(result.declineCode(), result.message());
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setPaymentReference(result.reference());
        booking.setAmountPaid(amount);
        Booking saved = bookingRepository.save(booking);

        Room room = roomService.getRoomById(saved.getRoomId());
        String actor = username(authentication);
        Map<String, Object> payload = basePayload(saved, room);
        payload.put("amountPaid", amount);
        payload.put("paymentReference", result.reference());
        audit(AuditEventType.BOOKING_CONFIRMED, actor, saved, payload, "Booking paid and confirmed");
        bookingEventPublisher.publish(BookingEventType.BOOKING_CONFIRMED, saved, actor, room.getName());

        return bookingMapper.toResponse(saved);
    }

    // ================= CANCEL =================

    @PreAuthorize("hasRole('ADMIN') or @bookingService.isOwner(#id, authentication.principal.id)")
    public CancellationQuote getCancellationQuote(Long id) {
        return cancellationPolicy.quote(findBooking(id));
    }

    @PreAuthorize("hasRole('ADMIN') or @bookingService.isOwner(#id, authentication.principal.id)")
    @Transactional
    public BookingResponse cancel(Long id, String reason, Authentication authentication) {
        Booking booking = findBooking(id);
        stateMachine.assertTransition(booking.getStatus(), BookingStatus.CANCELLED);

        CancellationQuote quote = cancellationPolicy.quote(booking);
        BigDecimal refund = quote.refundAmount();

        if (refund.signum() > 0) {
            if (booking.getPaymentReference() != null) {
                paymentGateway.refund(booking.getPaymentReference(), refund, BookingEvent.DEFAULT_CURRENCY);
            }
            booking.setPaymentStatus(refund.compareTo(quote.amountPaid()) >= 0
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(clock.instant());
        booking.setCancellationReason(normalizeReason(reason));
        booking.setRefundAmount(refund);
        Booking saved = bookingRepository.save(booking);

        Room room = roomService.getRoomById(saved.getRoomId());
        String actor = username(authentication);
        Map<String, Object> payload = basePayload(saved, room);
        payload.put("refundAmount", refund);
        payload.put("refundPercent", quote.refundPercent());
        audit(AuditEventType.BOOKING_CANCELLED, actor, saved, payload, "Booking cancelled successfully");
        bookingEventPublisher.publish(BookingEventType.BOOKING_CANCELLED, saved, actor, room.getName());

        return bookingMapper.toResponse(saved);
    }

    // ================= ADMIN LIFECYCLE =================

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BookingResponse checkIn(Long id, Authentication authentication) {
        Booking booking = findBooking(id);
        stateMachine.assertTransition(booking.getStatus(), BookingStatus.CHECKED_IN);
        if (LocalDate.now(clock).isBefore(booking.getCheckInDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Check-in is only possible on or after " + booking.getCheckInDate());
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(clock.instant());
        Booking saved = bookingRepository.save(booking);

        Room room = roomService.getRoomById(saved.getRoomId());
        String actor = username(authentication);
        audit(AuditEventType.BOOKING_CHECKED_IN, actor, saved, basePayload(saved, room), "Guest checked in");
        bookingEventPublisher.publish(BookingEventType.BOOKING_CHECKED_IN, saved, actor, room.getName());

        return bookingMapper.toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public BookingResponse complete(Long id) {
        Booking booking = findBooking(id);
        stateMachine.assertTransition(booking.getStatus(), BookingStatus.COMPLETED);

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(clock.instant());
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    // ================= HOLD EXPIRY =================

    /** Moves every PENDING booking whose hold has lapsed to EXPIRED; returns the number expired. */
    @Transactional
    public int expireLapsedHolds() {
        stateMachine.assertTransition(BookingStatus.PENDING, BookingStatus.EXPIRED);
        Instant now = clock.instant();
        return bookingRepository.expireHolds(now, LocalDateTime.now(clock));
    }

    // ================= HELPERS =================

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private static String username(Authentication authentication) {
        return ((CustomUserPrincipal) authentication.getPrincipal()).getUsername();
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > MAX_REASON_LENGTH ? trimmed.substring(0, MAX_REASON_LENGTH) : trimmed;
    }

    private static Map<String, Object> basePayload(Booking booking, Room room) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomName", room.getName());
        payload.put("checkInDate", booking.getCheckInDate());
        payload.put("checkOutDate", booking.getCheckOutDate());
        payload.put("status", booking.getStatus());
        payload.put("confirmationCode", booking.getConfirmationCode());
        if (booking.getTotalPrice() != null) {
            payload.put("totalPrice", booking.getTotalPrice());
        }
        return payload;
    }

    private void audit(AuditEventType type, String actor, Booking booking, Map<String, Object> payload, String message) {
        try {
            auditEventProducer.send(new AuditEventRequest(
                    UUID.randomUUID(), type, "booking-service", actor, "BOOKING", booking.getId(), payload, message));
        } catch (Exception e) {
            log.error("Failed to send audit event for booking {}", booking.getId(), e);
        }
    }
}
