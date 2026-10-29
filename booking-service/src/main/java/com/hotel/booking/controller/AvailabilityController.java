package com.hotel.booking.controller;

import com.hotel.booking.dto.AvailableRoom;
import com.hotel.booking.service.RoomSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class AvailabilityController {

    private final RoomSearchService roomSearchService;

    public AvailabilityController(RoomSearchService roomSearchService) {
        this.roomSearchService = roomSearchService;
    }

    // Public: rooms free for the whole stay, each with a price quote
    @GetMapping("/availability")
    public List<AvailableRoom> searchAvailability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String promoCode
    ) {
        return roomSearchService.findAvailableRooms(checkIn, checkOut, guests, type, minPrice, maxPrice, promoCode);
    }
}
