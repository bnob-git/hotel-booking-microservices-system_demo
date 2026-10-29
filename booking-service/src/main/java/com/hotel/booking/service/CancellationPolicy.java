package com.hotel.booking.service;

import com.hotel.booking.dto.BookingEvent;
import com.hotel.booking.dto.CancellationQuote;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Refund rules: at least 7 days before check-in 100%, 2–6 days 50%, under 2 days 0%.
 * Unpaid PENDING holds cancel free of charge with nothing to refund.
 */
@Component
public class CancellationPolicy {

    static final int FULL_REFUND_DAYS = 7;
    static final int PARTIAL_REFUND_DAYS = 2;

    private final Clock clock;
    private final BookingStateMachine stateMachine;

    public CancellationPolicy(Clock clock, BookingStateMachine stateMachine) {
        this.clock = clock;
        this.stateMachine = stateMachine;
    }

    public CancellationQuote quote(Booking booking) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), booking.getCheckInDate());
        BigDecimal paid = booking.getAmountPaid() == null ? BigDecimal.ZERO : booking.getAmountPaid();
        boolean cancellable = stateMachine.canTransition(booking.getStatus(), BookingStatus.CANCELLED);

        if (!cancellable) {
            return new CancellationQuote(booking.getId(), booking.getStatus(), false, days, 0,
                    paid, money(BigDecimal.ZERO), money(BigDecimal.ZERO), BookingEvent.DEFAULT_CURRENCY,
                    "Bookings in status " + booking.getStatus() + " cannot be cancelled");
        }

        if (booking.getStatus() == BookingStatus.PENDING) {
            return new CancellationQuote(booking.getId(), booking.getStatus(), true, days, 0,
                    paid, money(BigDecimal.ZERO), money(BigDecimal.ZERO), BookingEvent.DEFAULT_CURRENCY,
                    "Unpaid hold: cancelling is free of charge");
        }

        int percent = refundPercent(days);
        BigDecimal refund = money(paid.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        return new CancellationQuote(booking.getId(), booking.getStatus(), true, days, percent,
                paid, refund, money(paid.subtract(refund)), BookingEvent.DEFAULT_CURRENCY, describe(percent));
    }

    static int refundPercent(long daysBeforeCheckIn) {
        if (daysBeforeCheckIn >= FULL_REFUND_DAYS) {
            return 100;
        }
        if (daysBeforeCheckIn >= PARTIAL_REFUND_DAYS) {
            return 50;
        }
        return 0;
    }

    private static String describe(int percent) {
        return switch (percent) {
            case 100 -> "Full refund: cancelled 7 or more days before check-in";
            case 50 -> "50% refund: cancelled 2 to 6 days before check-in";
            default -> "No refund: cancelled less than 2 days before check-in";
        };
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
