package com.hotel.booking.dto;

import java.math.BigDecimal;

public record AvailableRoom(
        Long roomId,
        String name,
        String type,
        BigDecimal nightlyBaseRate,
        Integer maxGuests,
        int nights,
        PriceQuote quote
) {
}
