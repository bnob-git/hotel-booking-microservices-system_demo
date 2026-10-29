package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the V20 columns against the entity and the bulk hold-expiry update. */
@ActiveProfiles("test")
@DataJpaTest
class BookingHoldExpiryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired
    private BookingRepository bookingRepository;

    private Booking save(String code, BookingStatus status, Instant holdExpiresAt) {
        Booking b = new Booking(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 9L, 1L);
        b.setConfirmationCode(code);
        b.setStatus(status);
        b.setHoldExpiresAt(holdExpiresAt);
        return bookingRepository.saveAndFlush(b);
    }

    @Test
    void expiresOnlyPendingHoldsThatLapsed() {
        Long lapsed = save("HBEXP001", BookingStatus.PENDING, NOW.minusSeconds(1)).getId();
        Long exactlyNow = save("HBEXP002", BookingStatus.PENDING, NOW).getId();
        Long active = save("HBEXP003", BookingStatus.PENDING, NOW.plusSeconds(60)).getId();
        Long confirmed = save("HBEXP004", BookingStatus.CONFIRMED, NOW.minusSeconds(600)).getId();
        Long noHold = save("HBEXP005", BookingStatus.PENDING, null).getId();

        int updated = bookingRepository.expireHolds(NOW, LocalDateTime.of(2026, 11, 1, 10, 0));

        assertEquals(2, updated);
        assertEquals(BookingStatus.EXPIRED, bookingRepository.findById(lapsed).orElseThrow().getStatus());
        assertEquals(BookingStatus.EXPIRED, bookingRepository.findById(exactlyNow).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING, bookingRepository.findById(active).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(confirmed).orElseThrow().getStatus());
        assertEquals(BookingStatus.PENDING, bookingRepository.findById(noHold).orElseThrow().getStatus());
        assertFalse(bookingRepository.existsByRoomAndDateOverlap(9L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2))
                && bookingRepository.findAll().stream().allMatch(b -> b.getStatus() == BookingStatus.EXPIRED));
    }

    @Test
    void persistsPaymentAndLifecycleColumns() {
        Booking b = save("HBPAY001", BookingStatus.CANCELLED, NOW);
        assertEquals(PaymentStatus.UNPAID, b.getPaymentStatus());
        b.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        b.setPaymentReference("mock_pi_123");
        b.setAmountPaid(new BigDecimal("200.00"));
        b.setRefundAmount(new BigDecimal("100.00"));
        b.setCancelledAt(NOW);
        b.setCancellationReason("Change of plans");
        b.setCheckedInAt(NOW);
        b.setCompletedAt(NOW);
        bookingRepository.saveAndFlush(b);

        Booking loaded = bookingRepository.findById(b.getId()).orElseThrow();
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, loaded.getPaymentStatus());
        assertEquals("mock_pi_123", loaded.getPaymentReference());
        assertEquals(0, new BigDecimal("100.00").compareTo(loaded.getRefundAmount()));
        assertEquals(NOW, loaded.getCancelledAt());
        assertEquals(NOW, loaded.getHoldExpiresAt());
        assertEquals("Change of plans", loaded.getCancellationReason());
    }
}
