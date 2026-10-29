package com.hotel.booking.service;

import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.PaymentStatus;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.PromoCodeRepository;
import com.hotel.booking.repository.SeasonalRateRepository;
import com.hotel.booking.security.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingLifecycleFieldsTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingMapper bookingMapper;
    @Mock private RoomService roomService;
    @Mock private AuditEventProducer auditEventProducer;
    @Mock private BookingEventPublisher bookingEventPublisher;
    @Mock private ConfirmationCodeGenerator confirmationCodeGenerator;
    @Mock private BookingHoldPolicy bookingHoldPolicy;
    @Mock private BookingLifecycleService bookingLifecycleService;
    @Mock private Authentication authentication;

    @Spy
    private PricingService pricingService =
            new PricingService(mock(SeasonalRateRepository.class), mock(PromoCodeRepository.class));

    @InjectMocks private BookingService bookingService;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setId(10L);
        room.setName("Room 201");
        room.setPrice(new BigDecimal("120.00"));
    }

    private BookingRequest request(int nights, Integer guests) {
        BookingRequest dto = new BookingRequest();
        dto.setRoomId(10L);
        dto.setCheckInDate(LocalDate.now().plusDays(5));
        dto.setCheckOutDate(LocalDate.now().plusDays(5 + nights));
        dto.setNumGuests(guests);
        return dto;
    }

    private void authenticated() {
        when(authentication.getPrincipal()).thenReturn(new CustomUserPrincipal(
                1L, "alice", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void createBookingCreatesPendingHoldAndPublishesCreatedOnly() {
        authenticated();
        when(roomService.getRoomById(10L)).thenReturn(room);
        when(confirmationCodeGenerator.generate()).thenReturn("HBABC234");
        Instant holdExpiry = Instant.parse("2026-11-01T10:15:00Z");
        when(bookingHoldPolicy.newHoldExpiry()).thenReturn(holdExpiry);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingRequest dto = request(3, 2);
        dto.setSpecialRequests("  Late arrival  ");
        dto.setContactEmail("alice@example.com");

        bookingService.createBooking(dto, authentication);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertEquals(BookingStatus.PENDING, saved.getStatus());
        assertEquals(PaymentStatus.UNPAID, saved.getPaymentStatus());
        assertEquals(holdExpiry, saved.getHoldExpiresAt());
        assertEquals(0, new BigDecimal("360.00").compareTo(saved.getTotalPrice()));
        assertEquals(2, saved.getNumGuests());
        assertEquals("HBABC234", saved.getConfirmationCode());
        assertEquals("Late arrival", saved.getSpecialRequests());
        assertEquals("alice@example.com", saved.getContactEmail());

        verify(bookingEventPublisher).publish(BookingEventType.BOOKING_CREATED, saved, "alice", "Room 201");
        verify(bookingEventPublisher, never()).publish(eq(BookingEventType.BOOKING_CONFIRMED), any(), any(), any());
        verify(auditEventProducer, times(1)).send(argThat(e -> e.getEventType().name().equals("BOOKING_CREATED")));
    }

    @Test
    void createBookingDefaultsToOneGuest() {
        authenticated();
        when(roomService.getRoomById(10L)).thenReturn(room);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.createBooking(request(1, null), authentication);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getNumGuests());
    }

    @Test
    void createBookingRejectsInvalidGuestCount() {
        authenticated();
        when(roomService.getRoomById(10L)).thenReturn(room);

        for (int guests : new int[]{0, BookingService.MAX_GUESTS + 1}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> bookingService.createBooking(request(2, guests), authentication));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(bookingEventPublisher);
    }

    @Test
    void deleteBookingIsAnAliasForSoftCancel() {
        bookingService.deleteBooking(7L, authentication);

        verify(bookingLifecycleService).cancel(7L, null, authentication);
        verify(bookingRepository, never()).delete(any());
    }

    @Test
    void totalPriceIsNightlyRateTimesNights() {
        BigDecimal total = BookingService.calculateTotalPrice(room,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
        assertEquals(0, new BigDecimal("480.00").compareTo(total));
    }
}
