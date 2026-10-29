package com.hotel.booking.service;

import com.hotel.booking.dto.CancellationQuote;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CancellationPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
    private final CancellationPolicy policy = new CancellationPolicy(clock, new BookingStateMachine());

    private Booking paidBooking(int daysAhead, BookingStatus status) {
        Booking b = new Booking(TODAY.plusDays(daysAhead), TODAY.plusDays(daysAhead + 2), 1L, 1L);
        b.setId(5L);
        b.setStatus(status);
        b.setTotalPrice(new BigDecimal("300.00"));
        b.setAmountPaid(new BigDecimal("300.00"));
        return b;
    }

    @ParameterizedTest(name = "{0} days before check-in -> {1}% refund")
    @CsvSource({
            "30, 100, 300.00",
            "7, 100, 300.00",
            "6, 50, 150.00",
            "2, 50, 150.00",
            "1, 0, 0.00",
            "0, 0, 0.00"
    })
    void appliesRefundTiersAtBoundaries(int days, int percent, String refund) {
        CancellationQuote quote = policy.quote(paidBooking(days, BookingStatus.CONFIRMED));

        assertTrue(quote.cancellable());
        assertEquals(days, quote.daysBeforeCheckIn());
        assertEquals(percent, quote.refundPercent());
        assertEquals(new BigDecimal(refund), quote.refundAmount());
        assertEquals(new BigDecimal("300.00").subtract(new BigDecimal(refund)), quote.cancellationFee());
        assertEquals("EUR", quote.currency());
    }

    @Test
    void pendingHoldCancelsFreeWithNothingToRefund() {
        Booking pending = paidBooking(1, BookingStatus.PENDING);
        pending.setAmountPaid(null);

        CancellationQuote quote = policy.quote(pending);

        assertTrue(quote.cancellable());
        assertEquals(0, quote.refundPercent());
        assertEquals(new BigDecimal("0.00"), quote.refundAmount());
        assertEquals(new BigDecimal("0.00"), quote.cancellationFee());
        assertEquals(0, BigDecimal.ZERO.compareTo(quote.amountPaid()));
    }

    @Test
    void checkedInCompletedAndTerminalBookingsAreNotCancellable() {
        for (BookingStatus status : new BookingStatus[]{
                BookingStatus.CHECKED_IN, BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED}) {
            CancellationQuote quote = policy.quote(paidBooking(10, status));
            assertFalse(quote.cancellable(), status.name());
            assertEquals(new BigDecimal("0.00"), quote.refundAmount());
        }
    }

    @Test
    void roundsHalfRefundToCents() {
        Booking b = paidBooking(3, BookingStatus.CONFIRMED);
        b.setAmountPaid(new BigDecimal("125.55"));

        assertEquals(new BigDecimal("62.78"), policy.quote(b).refundAmount());
    }

    @Test
    void refundPercentTiers() {
        assertEquals(100, CancellationPolicy.refundPercent(7));
        assertEquals(50, CancellationPolicy.refundPercent(6));
        assertEquals(50, CancellationPolicy.refundPercent(2));
        assertEquals(0, CancellationPolicy.refundPercent(1));
        assertEquals(0, CancellationPolicy.refundPercent(-1));
    }
}
