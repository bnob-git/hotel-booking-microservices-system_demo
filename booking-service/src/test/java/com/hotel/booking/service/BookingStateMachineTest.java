package com.hotel.booking.service;

import com.hotel.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.hotel.booking.entity.BookingStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingStateMachineTest {

    private static final Map<BookingStatus, Set<BookingStatus>> EXPECTED = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED, EXPIRED),
            CONFIRMED, Set.of(CHECKED_IN, CANCELLED),
            CHECKED_IN, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            EXPIRED, Set.of()
    );

    private final BookingStateMachine stateMachine = new BookingStateMachine();

    static Stream<Arguments> allowedTransitions() {
        List<Arguments> args = new ArrayList<>();
        EXPECTED.forEach((from, targets) -> targets.forEach(to -> args.add(Arguments.of(from, to))));
        return args.stream();
    }

    static Stream<Arguments> illegalTransitions() {
        List<Arguments> args = new ArrayList<>();
        for (BookingStatus from : BookingStatus.values()) {
            for (BookingStatus to : BookingStatus.values()) {
                if (!EXPECTED.get(from).contains(to)) {
                    args.add(Arguments.of(from, to));
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @MethodSource("allowedTransitions")
    void allowsContractTransitions(BookingStatus from, BookingStatus to) {
        assertTrue(stateMachine.canTransition(from, to));
        assertDoesNotThrow(() -> stateMachine.assertTransition(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} rejected with 409")
    @MethodSource("illegalTransitions")
    void rejectsEveryOtherTransitionWithConflict(BookingStatus from, BookingStatus to) {
        assertFalse(stateMachine.canTransition(from, to));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> stateMachine.assertTransition(from, to));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void terminalStatesHaveNoTargets() {
        assertTrue(stateMachine.isTerminal(COMPLETED));
        assertTrue(stateMachine.isTerminal(CANCELLED));
        assertTrue(stateMachine.isTerminal(EXPIRED));
        assertFalse(stateMachine.isTerminal(PENDING));
        assertEquals(Set.of(CHECKED_IN, CANCELLED), stateMachine.allowedTargets(CONFIRMED));
    }

    @Test
    void nullStatusIsNeverAllowed() {
        assertFalse(stateMachine.canTransition(null, CONFIRMED));
        assertFalse(stateMachine.canTransition(PENDING, null));
    }
}
