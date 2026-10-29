package com.hotel.booking.dto;

import com.hotel.booking.entity.BookingStatus;

import java.math.BigDecimal;

/** Refund preview for cancelling a booking now ({@code GET /api/bookings/{id}/cancellation-quote}). */
public record CancellationQuote(
        Long bookingId,
        BookingStatus status,
        boolean cancellable,
        long daysBeforeCheckIn,
        int refundPercent,
        BigDecimal amountPaid,
        BigDecimal refundAmount,
        BigDecimal cancellationFee,
        String currency,
        String policy
) {
}
