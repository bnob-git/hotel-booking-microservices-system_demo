package com.hotel.booking.service;

import com.hotel.booking.dto.AvailableRoom;
import com.hotel.booking.dto.PriceQuote;
import com.hotel.booking.dto.PriceQuoteRequest;
import com.hotel.booking.entity.Room;
import com.hotel.booking.entity.SeasonalRate;
import com.hotel.booking.repository.PromoCodeRepository;
import com.hotel.booking.repository.RoomRepository;
import com.hotel.booking.repository.SeasonalRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomSearchServiceTest {

    private RoomRepository roomRepository;
    private RoomService roomService;
    private RoomSearchService roomSearchService;

    private final LocalDate checkIn = LocalDate.now().plusDays(10);
    private final LocalDate checkOut = checkIn.plusDays(2);

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        roomService = mock(RoomService.class);
        SeasonalRateRepository seasonalRateRepository = mock(SeasonalRateRepository.class);
        when(seasonalRateRepository.findByActiveTrue()).thenReturn(List.<SeasonalRate>of());
        PricingService pricingService = new PricingService(seasonalRateRepository, mock(PromoCodeRepository.class));
        roomSearchService = new RoomSearchService(roomRepository, roomService, pricingService);
    }

    private static Room room(long id, String name, String price) {
        Room room = new Room(name, "STANDARD", new BigDecimal(price), true);
        room.setId(id);
        room.setMaxGuests(2);
        return room;
    }

    @Test
    void returnsQuotedRoomsSortedByTotal() {
        when(roomRepository.findAvailableForStay(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(room(1L, "Room 101", "80.00"), room(2L, "Room 102", "60.00")));

        List<AvailableRoom> result = roomSearchService.findAvailableRooms(
                checkIn, checkOut, 2, " standard ", new BigDecimal("50"), new BigDecimal("100"), null);

        assertEquals(List.of(2L, 1L), result.stream().map(AvailableRoom::roomId).toList());
        AvailableRoom cheapest = result.get(0);
        assertEquals("Room 102", cheapest.name());
        assertEquals(2, cheapest.nights());
        assertEquals(2, cheapest.maxGuests());
        assertEquals(new BigDecimal("60.00"), cheapest.nightlyBaseRate());
        assertEquals(new BigDecimal("120.00"), cheapest.quote().totalPrice());
        verify(roomRepository).findAvailableForStay(checkIn, checkOut, 2, "STANDARD",
                new BigDecimal("50"), new BigDecimal("100"));
    }

    @Test
    void blankTypeMeansNoTypeFilter() {
        when(roomRepository.findAvailableForStay(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        assertTrue(roomSearchService.findAvailableRooms(checkIn, checkOut, null, " ", null, null, null).isEmpty());
        verify(roomRepository).findAvailableForStay(checkIn, checkOut, null, null, null, null);
    }

    @Test
    void reusesBookingDateRules() {
        LocalDate today = LocalDate.now();
        assertBadRequest(() -> roomSearchService.findAvailableRooms(null, checkOut, null, null, null, null, null),
                "Check-in and check-out dates are required");
        assertBadRequest(() -> roomSearchService.findAvailableRooms(checkIn, checkIn, null, null, null, null, null),
                "Check-out date must be after check-in date");
        assertBadRequest(() -> roomSearchService.findAvailableRooms(today.minusDays(1), today.plusDays(1),
                null, null, null, null, null), "Booking cannot start in the past");
        assertBadRequest(() -> roomSearchService.findAvailableRooms(today.plusYears(1), today.plusYears(1).plusDays(1),
                null, null, null, null, null), "Booking cannot be more than 1 year in advance");
        verifyNoInteractions(roomRepository);
    }

    @Test
    void rejectsInvalidGuestsAndPriceFilters() {
        assertBadRequest(() -> roomSearchService.findAvailableRooms(checkIn, checkOut, 0, null, null, null, null),
                "Number of guests must be between 1 and 10");
        assertBadRequest(() -> roomSearchService.findAvailableRooms(checkIn, checkOut, 11, null, null, null, null),
                "Number of guests must be between 1 and 10");
        assertBadRequest(() -> roomSearchService.findAvailableRooms(checkIn, checkOut, null, null,
                new BigDecimal("-1"), null, null), "Price filters must not be negative");
        assertBadRequest(() -> roomSearchService.findAvailableRooms(checkIn, checkOut, null, null,
                new BigDecimal("200"), new BigDecimal("100"), null), "Minimum price must not exceed maximum price");
        verifyNoInteractions(roomRepository);
    }

    @Test
    void quoteLooksUpRoomAndValidatesDates() {
        when(roomService.getRoomById(1L)).thenReturn(room(1L, "Room 101", "60.00"));

        PriceQuote quote = roomSearchService.quote(new PriceQuoteRequest(1L, checkIn, checkOut, null));

        assertEquals(new BigDecimal("120.00"), quote.totalPrice());
        assertBadRequest(() -> roomSearchService.quote(new PriceQuoteRequest(1L, checkOut, checkIn, null)),
                "Check-out date must be after check-in date");
        assertBadRequest(() -> roomSearchService.quote(new PriceQuoteRequest(null, checkIn, checkOut, null)),
                "Room id is required");
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable call, String message) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(message, ex.getReason());
    }
}
