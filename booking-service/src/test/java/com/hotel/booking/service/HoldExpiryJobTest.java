package com.hotel.booking.service;

import com.hotel.booking.kafka.AuditEventProducer;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.payment.PaymentGateway;
import com.hotel.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HoldExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void expiresLapsedHoldsInBulkUsingTheInjectedClock() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        BookingRepository repository = mock(BookingRepository.class);
        BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        BookingStateMachine stateMachine = new BookingStateMachine();
        BookingLifecycleService lifecycleService = new BookingLifecycleService(
                repository, mock(BookingMapper.class), mock(RoomService.class), mock(AuditEventProducer.class),
                publisher, stateMachine, new BookingHoldPolicy(clock, Duration.ofMinutes(15)),
                new CancellationPolicy(clock, stateMachine), mock(PaymentGateway.class), clock);
        when(repository.expireHolds(NOW, LocalDateTime.of(2026, 11, 1, 10, 0))).thenReturn(3);

        int expired = new HoldExpiryJob(lifecycleService).expireHolds();

        assertEquals(3, expired);
        verify(repository).expireHolds(NOW, LocalDateTime.of(2026, 11, 1, 10, 0));
        verifyNoInteractions(publisher);
    }

    @Test
    void reportsZeroWhenNothingLapsed() {
        BookingLifecycleService lifecycleService = mock(BookingLifecycleService.class);
        when(lifecycleService.expireLapsedHolds()).thenReturn(0);

        assertEquals(0, new HoldExpiryJob(lifecycleService).expireHolds());
    }
}
