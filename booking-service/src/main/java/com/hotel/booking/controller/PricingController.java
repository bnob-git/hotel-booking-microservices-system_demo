package com.hotel.booking.controller;

import com.hotel.booking.dto.PriceQuote;
import com.hotel.booking.dto.PriceQuoteRequest;
import com.hotel.booking.service.RoomSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final RoomSearchService roomSearchService;

    public PricingController(RoomSearchService roomSearchService) {
        this.roomSearchService = roomSearchService;
    }

    // Public: price breakdown for a stay, with optional promo code
    @PostMapping("/quote")
    public PriceQuote quote(@RequestBody PriceQuoteRequest request) {
        return roomSearchService.quote(request);
    }
}
