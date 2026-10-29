package com.hotel.booking.service;

import com.hotel.booking.entity.BookingStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed booking status transitions (see docs/modernization/CONTRACTS.md).
 * Stateless: callers apply the new status after {@link #assertTransition} succeeds.
 */
@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        TRANSITIONS.put(BookingStatus.PENDING,
                EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.EXPIRED));
        TRANSITIONS.put(BookingStatus.CONFIRMED,
                EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.CHECKED_IN, EnumSet.of(BookingStatus.COMPLETED));
        TRANSITIONS.put(BookingStatus.COMPLETED, EnumSet.noneOf(BookingStatus.class));
        TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
        TRANSITIONS.put(BookingStatus.EXPIRED, EnumSet.noneOf(BookingStatus.class));
    }

    public boolean canTransition(BookingStatus from, BookingStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.get(from).contains(to);
    }

    public Set<BookingStatus> allowedTargets(BookingStatus from) {
        return from == null ? Set.of() : Set.copyOf(TRANSITIONS.get(from));
    }

    public boolean isTerminal(BookingStatus status) {
        return allowedTargets(status).isEmpty();
    }

    /**
     * @throws ResponseStatusException 409 CONFLICT when the transition is not allowed
     */
    public void assertTransition(BookingStatus from, BookingStatus to) {
        if (!canTransition(from, to)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot change booking status from " + from + " to " + to
            );
        }
    }
}
