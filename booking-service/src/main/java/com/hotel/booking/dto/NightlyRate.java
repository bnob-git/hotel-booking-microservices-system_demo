package com.hotel.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One night of a {@link PriceQuote}: rate = baseRate x multiplier (HALF_UP, 2 dp). */
public record NightlyRate(
        LocalDate date,
        BigDecimal baseRate,
        BigDecimal multiplier,
        BigDecimal rate,
        String season
) {
}
