package com.hotel.booking.service;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.Room;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.security.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private RoomService roomService;

    @Mock
    private AuditEventProducer auditEventProducer;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private BookingService bookingService;

    private CustomUserPrincipal user;
    private Room room;

    @BeforeEach
    void setUp() {
        user = new CustomUserPrincipal(
                1L,
                "testuser",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        room = new Room(
                "Test Room",
                "STANDARD",
                BigDecimal.valueOf(100),
                true
        );

        room.setId(1L);
    }

    @Test
    void shouldReturnBookingsPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 2);

        Booking booking = new Booking(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                room.getId(),
                user.getId()
        );

        BookingResponse response = new BookingResponse(
                1L,
                user.getId(),
                room.getId(),
                "testuser",
                "Test Room",
                booking.getCheckInDate(),
                booking.getCheckOutDate()
        );

        when(bookingRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(booking), pageable, 5));

        when(bookingMapper.toResponse(booking))
                .thenReturn(response);

        // Act
        Page<BookingResponse> page = bookingService.getAllBookings(pageable);

        // Assert
        assertEquals(1, page.getContent().size());
        assertEquals(response, page.getContent().get(0));
        assertEquals(5, page.getTotalElements());
        assertEquals(3, page.getTotalPages());

        verify(bookingRepository).findAll(pageable);
    }

    @Test
    void shouldCreateBookingSuccessfully() {
        // Arrange
        BookingRequest request = createRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3)
        );

        Booking savedBooking = new Booking(
                request.getCheckInDate(),
                request.getCheckOutDate(),
                room.getId(),
                user.getId()
        );

        savedBooking.setId(1L);

        BookingResponse expectedResponse = mock(BookingResponse.class);

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);
        when(bookingRepository.existsByRoomAndDateOverlap(
                eq(room.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(bookingMapper.toResponse(savedBooking)).thenReturn(expectedResponse);

        // Act
        BookingResponse result =
                bookingService.createBooking(request, authentication);

        // Assert
        assertSame(expectedResponse, result);

        verify(bookingRepository).save(any(Booking.class));
        verify(auditEventProducer).send(any());

        ArgumentCaptor<Booking> captor =
                ArgumentCaptor.forClass(Booking.class);

        verify(bookingRepository).save(captor.capture());

        Booking createdBooking = captor.getValue();

        assertEquals(room.getId(), createdBooking.getRoomId());
        assertEquals(user.getId(), createdBooking.getUserId());
        assertEquals(request.getCheckInDate(), createdBooking.getCheckInDate());
        assertEquals(request.getCheckOutDate(), createdBooking.getCheckOutDate());
    }

    @Test
    void shouldRejectBookingInPast() {
        // Arrange
        BookingRequest request = createRequest(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(2)
        );

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.createBooking(request, authentication)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertEquals(
                "Booking cannot start in the past",
                exception.getReason()
        );

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidDateRange() {
        // Arrange
        BookingRequest request = createRequest(
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(3)
        );

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.createBooking(request, authentication)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertEquals(
                "Check-out date must be after check-in date",
                exception.getReason()
        );

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectOverlappingBooking() {
        // Arrange
        BookingRequest request = createRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3)
        );

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);

        when(bookingRepository.existsByRoomAndDateOverlap(
                eq(room.getId()),
                eq(request.getCheckInDate()),
                eq(request.getCheckOutDate())
        )).thenReturn(true);

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.createBooking(request, authentication)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(
                "Room already booked for these dates",
                exception.getReason()
        );

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectBookingWhenDatesAreNull() {
        // Arrange
        BookingRequest request = createRequest(null, null);

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.createBooking(request, authentication)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertEquals(
                "Check-in and check-out dates are required",
                exception.getReason()
        );

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectBookingMoreThanOneYearInAdvance() {
        // Arrange
        BookingRequest request = createRequest(
                LocalDate.now().plusYears(1).plusDays(1),
                LocalDate.now().plusYears(1).plusDays(3)
        );

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.createBooking(request, authentication)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertEquals(
                "Booking cannot be more than 1 year in advance",
                exception.getReason()
        );

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldCreateBookingEvenWhenAuditEventFails() {
        // Arrange
        BookingRequest request = createRequest(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3)
        );

        Booking savedBooking = new Booking(
                request.getCheckInDate(),
                request.getCheckOutDate(),
                room.getId(),
                user.getId()
        );

        savedBooking.setId(1L);

        BookingResponse expectedResponse = mock(BookingResponse.class);

        when(authentication.getPrincipal()).thenReturn(user);
        when(roomService.getRoomById(room.getId())).thenReturn(room);
        when(bookingRepository.existsByRoomAndDateOverlap(
                eq(room.getId()),
                eq(request.getCheckInDate()),
                eq(request.getCheckOutDate())
        )).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(bookingMapper.toResponse(savedBooking)).thenReturn(expectedResponse);

        doThrow(new RuntimeException("Kafka unavailable"))
                .when(auditEventProducer)
                .send(any());

        // Act
        BookingResponse result =
                bookingService.createBooking(request, authentication);

        // Assert
        assertSame(expectedResponse, result);

        verify(bookingRepository).save(any(Booking.class));
        verify(auditEventProducer).send(any());
        verify(bookingMapper).toResponse(savedBooking);
    }

    private BookingRequest createRequest(
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        BookingRequest request = new BookingRequest();
        request.setRoomId(room.getId());
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkOut);
        return request;
    }
}
