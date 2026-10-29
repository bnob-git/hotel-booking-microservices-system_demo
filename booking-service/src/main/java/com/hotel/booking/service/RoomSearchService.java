package com.hotel.booking.service;

import com.hotel.booking.dto.AvailableRoom;
import com.hotel.booking.dto.PriceQuote;
import com.hotel.booking.dto.PriceQuoteRequest;
import com.hotel.booking.entity.Room;
import com.hotel.booking.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Public availability search and price quotes (Track A). */
@Service
public class RoomSearchService {

    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final PricingService pricingService;

    public RoomSearchService(RoomRepository roomRepository, RoomService roomService, PricingService pricingService) {
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public List<AvailableRoom> findAvailableRooms(LocalDate checkIn,
                                                  LocalDate checkOut,
                                                  Integer guests,
                                                  String type,
                                                  BigDecimal minPrice,
                                                  BigDecimal maxPrice,
                                                  String promoCode) {

        BookingService.validateBookingDates(checkIn, checkOut);
        validateGuests(guests);
        validatePriceRange(minPrice, maxPrice);

        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);

        List<Room> rooms = roomRepository.findAvailableForStay(
                checkIn, checkOut, guests, normalizedType, minPrice, maxPrice);
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<PriceQuote> quotes = pricingService.quoteAll(rooms, checkIn, checkOut, promoCode);

        List<AvailableRoom> result = new ArrayList<>(rooms.size());
        for (int i = 0; i < rooms.size(); i++) {
            Room room = rooms.get(i);
            PriceQuote quote = quotes.get(i);
            result.add(new AvailableRoom(
                    room.getId(),
                    room.getName(),
                    room.getType(),
                    quote.nightly().isEmpty() ? room.getPrice() : quote.nightly().get(0).baseRate(),
                    room.getMaxGuests(),
                    quote.nights(),
                    quote
            ));
        }
        result.sort(Comparator
                .comparing((AvailableRoom r) -> r.quote().totalPrice())
                .thenComparing(AvailableRoom::roomId));
        return result;
    }

    public PriceQuote quote(PriceQuoteRequest request) {
        if (request == null || request.roomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room id is required");
        }
        Room room = roomService.getRoomById(request.roomId());
        BookingService.validateBookingDates(request.checkInDate(), request.checkOutDate());
        return pricingService.quote(room, request.checkInDate(), request.checkOutDate(), request.promoCode());
    }

    private static void validateGuests(Integer guests) {
        if (guests != null && (guests < 1 || guests > BookingService.MAX_GUESTS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Number of guests must be between 1 and " + BookingService.MAX_GUESTS
            );
        }
    }

    private static void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price filters must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum price must not exceed maximum price");
        }
    }
}
