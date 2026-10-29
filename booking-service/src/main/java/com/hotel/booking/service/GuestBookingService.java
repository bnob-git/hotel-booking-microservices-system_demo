package com.hotel.booking.service;

import com.hotel.booking.dto.AuditEventRequest;
import com.hotel.booking.dto.AuditEventType;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.GuestBookingRequest;
import com.hotel.booking.dto.GuestCancelRequest;
import com.hotel.booking.dto.PriceQuote;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Guest checkout (Track D): bookings without a user account, identified by
 * confirmation code + contact email. Uses the same date/guest/overlap rules as {@link BookingService}.
 */
@Service
public class GuestBookingService {

    static final int MAX_NAME_LENGTH = 255;
    static final int MAX_EMAIL_LENGTH = 255;
    static final int MAX_SPECIAL_REQUESTS_LENGTH = 1000;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<BookingStatus> CANCELLABLE =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    private static final Logger log = LoggerFactory.getLogger(GuestBookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final RoomService roomService;
    private final AuditEventProducer auditEventProducer;
    private final BookingEventPublisher bookingEventPublisher;
    private final ConfirmationCodeGenerator confirmationCodeGenerator;
    private final PricingService pricingService;

    public GuestBookingService(
            BookingRepository bookingRepository,
            BookingMapper bookingMapper,
            RoomService roomService,
            AuditEventProducer auditEventProducer,
            BookingEventPublisher bookingEventPublisher,
            ConfirmationCodeGenerator confirmationCodeGenerator,
            PricingService pricingService
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.roomService = roomService;
        this.auditEventProducer = auditEventProducer;
        this.bookingEventPublisher = bookingEventPublisher;
        this.confirmationCodeGenerator = confirmationCodeGenerator;
        this.pricingService = pricingService;
    }

    // ================= CREATE =================

    @Transactional
    public BookingResponse createGuestBooking(GuestBookingRequest dto) {

        String guestName = requireText(dto.getGuestName(), "Guest name is required", MAX_NAME_LENGTH);
        String guestEmail = validateEmail(dto.getGuestEmail());
        String specialRequests = trimToNull(dto.getSpecialRequests());
        if (specialRequests != null && specialRequests.length() > MAX_SPECIAL_REQUESTS_LENGTH) {
            throw badRequest("Special requests must be at most " + MAX_SPECIAL_REQUESTS_LENGTH + " characters");
        }
        if (dto.getRoomId() == null) {
            throw badRequest("Room is required");
        }

        Room room = roomService.getRoomById(dto.getRoomId());  // validate room exists

        BookingService.validateBookingDates(dto.getCheckInDate(), dto.getCheckOutDate());
        int numGuests = BookingService.validateNumGuests(dto.getNumGuests());

        if (bookingRepository.existsByRoomAndDateOverlap(room.getId(), dto.getCheckInDate(), dto.getCheckOutDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room already booked for these dates");
        }

        Booking booking = new Booking();
        booking.setRoomId(room.getId());
        booking.setUserId(null);
        booking.setGuestName(guestName);
        booking.setContactEmail(guestEmail);
        booking.setCheckInDate(dto.getCheckInDate());
        booking.setCheckOutDate(dto.getCheckOutDate());
        booking.setNumGuests(numGuests);
        booking.setSpecialRequests(specialRequests);
        PriceQuote quote = pricingService.quote(room, dto.getCheckInDate(), dto.getCheckOutDate(), trimToNull(dto.getPromoCode()));
        if (quote.promoCode() != null && !quote.promoApplied()) {
            throw badRequest(quote.promoMessage());
        }
        booking.setBaseAmount(quote.baseAmount());
        booking.setSeasonalAdjustment(quote.seasonalAdjustment());
        booking.setPromoCode(quote.promoApplied() ? quote.promoCode() : null);
        booking.setPromoDiscount(quote.promoDiscount());
        booking.setTotalPrice(quote.totalPrice());
        booking.setConfirmationCode(confirmationCodeGenerator.generate());
        // Guests pay at the hotel, so guest bookings are confirmed immediately (no online payment hold).
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking saved = bookingRepository.save(booking);

        sendAudit(AuditEventType.BOOKING_CREATED, saved, room, "Guest booking created successfully");

        bookingEventPublisher.publish(BookingEventType.BOOKING_CREATED, saved, guestName, room.getName());
        bookingEventPublisher.publish(BookingEventType.BOOKING_CONFIRMED, saved, guestName, room.getName());

        return bookingMapper.toResponse(saved);
    }

    // ================= LOOKUP =================

    @Transactional(readOnly = true)
    public BookingResponse lookup(String code, String email) {
        return bookingMapper.toResponse(findByCodeAndEmail(code, email));
    }

    // ================= CANCEL =================

    @Transactional
    public BookingResponse cancel(GuestCancelRequest request) {

        Booking booking = findByCodeAndEmail(request.getCode(), request.getEmail());

        if (booking.getUserId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This booking belongs to a registered account; please sign in to cancel it"
            );
        }
        if (!CANCELLABLE.contains(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking cannot be cancelled in status " + booking.getStatus()
            );
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);

        Room room = roomService.getRoomById(saved.getRoomId());
        sendAudit(AuditEventType.BOOKING_CANCELLED, saved, room, "Guest booking cancelled successfully");
        bookingEventPublisher.publish(BookingEventType.BOOKING_CANCELLED, saved, saved.getGuestName(), room.getName());

        return bookingMapper.toResponse(saved);
    }

    // ================= HELPERS =================

    /** Both code and email must match; any mismatch is a plain 404 so bookings cannot be enumerated. */
    private Booking findByCodeAndEmail(String code, String email) {
        String normalizedCode = trimToNull(code);
        String normalizedEmail = trimToNull(email);
        if (normalizedCode == null || normalizedEmail == null) {
            throw notFound();
        }
        return bookingRepository.findByConfirmationCode(normalizedCode.toUpperCase(Locale.ROOT))
                .filter(b -> b.getContactEmail() != null
                        && b.getContactEmail().equalsIgnoreCase(normalizedEmail))
                .orElseThrow(GuestBookingService::notFound);
    }

    private void sendAudit(AuditEventType type, Booking booking, Room room, String message) {
        Map<String, Object> payload = Map.of(
                "roomName", room.getName(),
                "checkInDate", booking.getCheckInDate(),
                "checkOutDate", booking.getCheckOutDate(),
                "guest", true
        );
        AuditEventRequest auditEvent = new AuditEventRequest(
                UUID.randomUUID(),
                type,
                "booking-service",
                "guest:" + booking.getGuestName(),
                "BOOKING",
                booking.getId(),
                payload,
                message
        );
        try {
            auditEventProducer.send(auditEvent);
        } catch (Exception e) {
            log.error("Failed to send audit event for guest booking {}", booking.getId(), e);
        }
    }

    private static String validateEmail(String email) {
        String value = requireText(email, "Guest email is required", MAX_EMAIL_LENGTH);
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw badRequest("Guest email is invalid");
        }
        return value;
    }

    private static String requireText(String value, String message, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw badRequest(message);
        }
        if (trimmed.length() > maxLength) {
            throw badRequest(message.replace(" is required", "") + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
    }
}
