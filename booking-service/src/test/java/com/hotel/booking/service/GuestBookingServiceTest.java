package com.hotel.booking.service;

import com.hotel.booking.dto.AuditEventRequest;
import com.hotel.booking.dto.AuditEventType;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.GuestBookingRequest;
import com.hotel.booking.dto.GuestCancelRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import com.hotel.booking.repository.PromoCodeRepository;
import com.hotel.booking.repository.SeasonalRateRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestBookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private RoomService roomService;

    @Mock
    private AuditEventProducer auditEventProducer;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private ConfirmationCodeGenerator confirmationCodeGenerator;

    @Spy
    private PricingService pricingService =
            new PricingService(mock(SeasonalRateRepository.class), mock(PromoCodeRepository.class));

    @InjectMocks
    private GuestBookingService guestBookingService;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("Room 201", "DOUBLE", BigDecimal.valueOf(120), true);
        room.setId(4L);
    }

    // ================= CREATE =================

    @Test
    void shouldCreateConfirmedGuestBookingAndPublishEvents() {
        GuestBookingRequest request = validRequest();
        when(roomService.getRoomById(4L)).thenReturn(room);
        when(confirmationCodeGenerator.generate()).thenReturn("HBABC234");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(42L);
            return b;
        });
        BookingResponse response = mock(BookingResponse.class);
        when(bookingMapper.toResponse(any(Booking.class))).thenReturn(response);

        assertSame(response, guestBookingService.createGuestBooking(request));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertNull(saved.getUserId());
        assertEquals("Jane Guest", saved.getGuestName());
        assertEquals("jane@example.com", saved.getContactEmail());
        assertEquals(BookingStatus.CONFIRMED, saved.getStatus());
        assertEquals(2, saved.getNumGuests());
        assertEquals("HBABC234", saved.getConfirmationCode());
        assertEquals("Late arrival", saved.getSpecialRequests());
        assertEquals(0, BigDecimal.valueOf(360).compareTo(saved.getTotalPrice()));

        verify(bookingEventPublisher).publish(BookingEventType.BOOKING_CREATED, saved, "Jane Guest", "Room 201");
        verify(bookingEventPublisher).publish(BookingEventType.BOOKING_CONFIRMED, saved, "Jane Guest", "Room 201");

        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(audit.capture());
        assertEquals(AuditEventType.BOOKING_CREATED, audit.getValue().getEventType());
    }

    @Test
    void shouldStillCreateBookingWhenAuditFails() {
        when(roomService.getRoomById(4L)).thenReturn(room);
        when(confirmationCodeGenerator.generate()).thenReturn("HBABC234");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("kafka down")).when(auditEventProducer).send(any());

        assertDoesNotThrow(() -> guestBookingService.createGuestBooking(validRequest()));
        verify(bookingEventPublisher, times(2)).publish(any(), any(), any(), any());
    }

    @Test
    void shouldRejectMissingGuestName() {
        GuestBookingRequest request = validRequest();
        request.setGuestName("  ");
        assertBadRequest(request, "Guest name is required");
    }

    @Test
    void shouldRejectMissingEmail() {
        GuestBookingRequest request = validRequest();
        request.setGuestEmail(null);
        assertBadRequest(request, "Guest email is required");
    }

    @Test
    void shouldRejectInvalidEmail() {
        GuestBookingRequest request = validRequest();
        request.setGuestEmail("not-an-email");
        assertBadRequest(request, "Guest email is invalid");
    }

    @Test
    void shouldRejectMissingRoom() {
        GuestBookingRequest request = validRequest();
        request.setRoomId(null);
        assertBadRequest(request, "Room is required");
    }

    @Test
    void shouldRejectTooLongSpecialRequests() {
        GuestBookingRequest request = validRequest();
        request.setSpecialRequests("x".repeat(1001));
        assertBadRequest(request, "Special requests must be at most 1000 characters");
    }

    @Test
    void shouldRejectCheckOutBeforeCheckIn() {
        GuestBookingRequest request = validRequest();
        request.setCheckOutDate(request.getCheckInDate());
        when(roomService.getRoomById(4L)).thenReturn(room);
        assertBadRequest(request, "Check-out date must be after check-in date");
    }

    @Test
    void shouldRejectPastCheckIn() {
        GuestBookingRequest request = validRequest();
        request.setCheckInDate(LocalDate.now().minusDays(1));
        when(roomService.getRoomById(4L)).thenReturn(room);
        assertBadRequest(request, "Booking cannot start in the past");
    }

    @Test
    void shouldRejectTooManyGuests() {
        GuestBookingRequest request = validRequest();
        request.setNumGuests(BookingService.MAX_GUESTS + 1);
        when(roomService.getRoomById(4L)).thenReturn(room);
        assertBadRequest(request, "Number of guests must be between 1 and " + BookingService.MAX_GUESTS);
    }

    @Test
    void shouldRejectOverlappingBooking() {
        GuestBookingRequest request = validRequest();
        when(roomService.getRoomById(4L)).thenReturn(room);
        when(bookingRepository.existsByRoomAndDateOverlap(eq(4L), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guestBookingService.createGuestBooking(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(bookingEventPublisher);
    }

    // ================= LOOKUP =================

    @Test
    void shouldLookupWhenCodeAndEmailMatch() {
        Booking booking = guestBooking(BookingStatus.CONFIRMED);
        when(bookingRepository.findByConfirmationCode("HBABC234")).thenReturn(Optional.of(booking));
        BookingResponse response = mock(BookingResponse.class);
        when(bookingMapper.toResponse(booking)).thenReturn(response);

        assertSame(response, guestBookingService.lookup("HBABC234", "jane@example.com"));
    }

    @Test
    void shouldLookupWithCaseInsensitiveEmailAndCode() {
        Booking booking = guestBooking(BookingStatus.CONFIRMED);
        when(bookingRepository.findByConfirmationCode("HBABC234")).thenReturn(Optional.of(booking));
        when(bookingMapper.toResponse(booking)).thenReturn(mock(BookingResponse.class));

        assertNotNull(guestBookingService.lookup(" hbabc234 ", " JANE@Example.COM "));
    }

    @Test
    void shouldReturn404ForWrongEmail() {
        when(bookingRepository.findByConfirmationCode("HBABC234"))
                .thenReturn(Optional.of(guestBooking(BookingStatus.CONFIRMED)));
        assertNotFound(() -> guestBookingService.lookup("HBABC234", "other@example.com"));
    }

    @Test
    void shouldReturn404ForWrongCode() {
        when(bookingRepository.findByConfirmationCode("HBZZZZZZ")).thenReturn(Optional.empty());
        assertNotFound(() -> guestBookingService.lookup("HBZZZZZZ", "jane@example.com"));
    }

    @Test
    void shouldReturn404WhenCodeOrEmailMissing() {
        assertNotFound(() -> guestBookingService.lookup(null, "jane@example.com"));
        assertNotFound(() -> guestBookingService.lookup("HBABC234", " "));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void shouldReturn404WhenBookingHasNoContactEmail() {
        Booking booking = guestBooking(BookingStatus.CONFIRMED);
        booking.setContactEmail(null);
        when(bookingRepository.findByConfirmationCode("HBABC234")).thenReturn(Optional.of(booking));
        assertNotFound(() -> guestBookingService.lookup("HBABC234", "jane@example.com"));
    }

    @Test
    void shouldLookupCancelledBooking() {
        Booking booking = guestBooking(BookingStatus.CANCELLED);
        when(bookingRepository.findByConfirmationCode("HBABC234")).thenReturn(Optional.of(booking));
        when(bookingMapper.toResponse(booking)).thenReturn(BookingResponse.of(booking, "Jane Guest", "Room 201"));

        assertEquals(BookingStatus.CANCELLED,
                guestBookingService.lookup("HBABC234", "jane@example.com").getStatus());
    }

    // ================= CANCEL =================

    @Test
    void shouldSoftCancelGuestBooking() {
        Booking booking = guestBooking(BookingStatus.CONFIRMED);
        when(bookingRepository.findByConfirmationCode("HBABC234")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(roomService.getRoomById(4L)).thenReturn(room);

        guestBookingService.cancel(new GuestCancelRequest("HBABC234", "JANE@example.com"));

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(bookingRepository).save(booking);
        verify(bookingRepository, never()).delete(any());
        verify(bookingEventPublisher).publish(BookingEventType.BOOKING_CANCELLED, booking, "Jane Guest", "Room 201");
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(audit.capture());
        assertEquals(AuditEventType.BOOKING_CANCELLED, audit.getValue().getEventType());
    }

    @Test
    void shouldRejectCancellingAlreadyCancelledBooking() {
        when(bookingRepository.findByConfirmationCode("HBABC234"))
                .thenReturn(Optional.of(guestBooking(BookingStatus.CANCELLED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guestBookingService.cancel(new GuestCancelRequest("HBABC234", "jane@example.com")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(bookingEventPublisher);
    }

    @Test
    void shouldRejectCancellingRegisteredUsersBooking() {
        Booking booking = guestBooking(BookingStatus.CONFIRMED);
        booking.setUserId(3L);
        when(bookingRepository.findByConfirmationCode("HBABC234")).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guestBookingService.cancel(new GuestCancelRequest("HBABC234", "jane@example.com")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldReturn404WhenCancellingWithWrongEmail() {
        when(bookingRepository.findByConfirmationCode("HBABC234"))
                .thenReturn(Optional.of(guestBooking(BookingStatus.CONFIRMED)));
        assertNotFound(() -> guestBookingService.cancel(new GuestCancelRequest("HBABC234", "x@example.com")));
        verify(bookingRepository, never()).save(any());
    }

    // ================= HELPERS =================

    private GuestBookingRequest validRequest() {
        GuestBookingRequest request = new GuestBookingRequest();
        request.setRoomId(4L);
        request.setCheckInDate(LocalDate.now().plusDays(10));
        request.setCheckOutDate(LocalDate.now().plusDays(13));
        request.setNumGuests(2);
        request.setGuestName("  Jane Guest ");
        request.setGuestEmail(" jane@example.com ");
        request.setSpecialRequests("Late arrival");
        return request;
    }

    private Booking guestBooking(BookingStatus status) {
        Booking booking = new Booking(LocalDate.now().plusDays(10), LocalDate.now().plusDays(13), 4L, null);
        booking.setId(42L);
        booking.setGuestName("Jane Guest");
        booking.setContactEmail("jane@example.com");
        booking.setConfirmationCode("HBABC234");
        booking.setStatus(status);
        return booking;
    }

    private void assertBadRequest(GuestBookingRequest request, String message) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guestBookingService.createGuestBooking(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(message, ex.getReason());
        verify(bookingRepository, never()).save(any());
    }

    private void assertNotFound(org.junit.jupiter.api.function.Executable executable) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, executable);
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Booking not found", ex.getReason());
    }
}
