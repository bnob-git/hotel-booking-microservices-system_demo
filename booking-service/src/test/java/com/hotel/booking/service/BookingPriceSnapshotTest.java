package com.hotel.booking.service;

import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.PromoCode;
import com.hotel.booking.entity.PromoType;
import com.hotel.booking.entity.Room;
import com.hotel.booking.entity.SeasonalRate;
import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.PromoCodeRepository;
import com.hotel.booking.repository.SeasonalRateRepository;
import com.hotel.booking.security.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** createBooking prices the stay through PricingService and stores the snapshot. */
class BookingPriceSnapshotTest {

    private BookingRepository bookingRepository;
    private SeasonalRateRepository seasonalRateRepository;
    private BookingService bookingService;
    private Authentication authentication;
    private Room room;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomService roomService = mock(RoomService.class);
        room = new Room("Room 201", "DELUXE", new BigDecimal("120.00"), true);
        room.setId(4L);
        when(roomService.getRoomById(4L)).thenReturn(room);

        seasonalRateRepository = mock(SeasonalRateRepository.class);
        when(seasonalRateRepository.findByActiveTrue()).thenReturn(List.of());

        PromoCode welcome = new PromoCode("WELCOME10", PromoType.PERCENT, new BigDecimal("10.00"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2099, 12, 31));
        PromoCode expired = new PromoCode("EXPIRED20", PromoType.PERCENT, new BigDecimal("20.00"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        PromoCodeRepository promoCodeRepository = mock(PromoCodeRepository.class);
        when(promoCodeRepository.findByCodeIgnoreCase(anyString())).thenAnswer(inv -> switch (inv.<String>getArgument(0)) {
            case "WELCOME10" -> Optional.of(welcome);
            case "EXPIRED20" -> Optional.of(expired);
            default -> Optional.empty();
        });

        bookingService = new BookingService(bookingRepository, mock(BookingMapper.class), roomService,
                mock(AuditEventProducer.class), mock(BookingEventPublisher.class),
                mock(ConfirmationCodeGenerator.class),
                new PricingService(seasonalRateRepository, promoCodeRepository),
                new BookingHoldPolicy(java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(15)),
                mock(BookingLifecycleService.class));

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new CustomUserPrincipal(
                1L, "alice", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private BookingRequest request(String promo) {
        BookingRequest dto = new BookingRequest();
        dto.setRoomId(4L);
        dto.setCheckInDate(LocalDate.now().plusDays(5));
        dto.setCheckOutDate(LocalDate.now().plusDays(8));
        dto.setPromoCode(promo);
        return dto;
    }

    private Booking saved() {
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void storesSnapshotWithPromo() {
        bookingService.createBooking(request("welcome10"), authentication);

        Booking b = saved();
        assertEquals(new BigDecimal("360.00"), b.getBaseAmount());
        assertEquals(new BigDecimal("0.00"), b.getSeasonalAdjustment());
        assertEquals("WELCOME10", b.getPromoCode());
        assertEquals(new BigDecimal("36.00"), b.getPromoDiscount());
        assertEquals(new BigDecimal("324.00"), b.getTotalPrice());
    }

    @Test
    void storesSeasonalAdjustment() {
        // Every night seasonal, regardless of when the test runs
        when(seasonalRateRepository.findByActiveTrue()).thenReturn(List.of(
                new SeasonalRate("ALL", "All year", "01-01", "12-31", new BigDecimal("1.2500"))));

        bookingService.createBooking(request(null), authentication);

        Booking b = saved();
        assertEquals(new BigDecimal("360.00"), b.getBaseAmount());
        assertEquals(new BigDecimal("90.00"), b.getSeasonalAdjustment());
        assertNull(b.getPromoCode());
        assertEquals(new BigDecimal("0.00"), b.getPromoDiscount());
        assertEquals(new BigDecimal("450.00"), b.getTotalPrice());
    }

    @Test
    void invalidOrExpiredPromoRejectsBooking() {
        for (String code : new String[]{"EXPIRED20", "BOGUS"}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> bookingService.createBooking(request(code), authentication));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().startsWith("Promo code " + code));
        }
        verify(bookingRepository, never()).save(any());
    }
}
