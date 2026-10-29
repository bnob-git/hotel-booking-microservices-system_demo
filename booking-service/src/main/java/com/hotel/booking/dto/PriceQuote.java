package com.hotel.booking.dto;

import java.math.BigDecimal;
import java.util.List;

/** Price breakdown for a stay (see docs/modernization/CONTRACTS.md, Track A). */
public record PriceQuote(
        Long roomId,
        int nights,
        String currency,
        BigDecimal baseAmount,
        BigDecimal seasonalAdjustment,
        String promoCode,
        BigDecimal promoDiscount,
        boolean promoApplied,
        String promoMessage,
        BigDecimal totalPrice,
        List<NightlyRate> nightly
) {
}
