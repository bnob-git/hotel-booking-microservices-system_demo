package com.hotel.booking.dto;

import java.time.LocalDate;

public record PriceQuoteRequest(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String promoCode
) {
}
