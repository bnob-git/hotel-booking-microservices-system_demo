package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against the Flyway-migrated schema (ddl-auto=validate), so it also verifies V1/V2. */
@ActiveProfiles("test")
@DataJpaTest
class BookingLifecycleRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    private Booking booking(Long roomId, LocalDate in, LocalDate out, BookingStatus status, String code) {
        Booking b = new Booking(in, out, roomId, 1L);
        b.setStatus(status);
        b.setConfirmationCode(code);
        return b;
    }

    @Test
    void persistsLifecycleFieldsAndDefaults() {
        Booking b = new Booking(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 1L, 1L);
        b.setTotalPrice(new BigDecimal("240.00"));
        b.setSpecialRequests("Quiet room");
        Booking saved = bookingRepository.saveAndFlush(b);

        Booking loaded = bookingRepository.findById(saved.getId()).orElseThrow();
        assertEquals(BookingStatus.PENDING, loaded.getStatus());
        assertEquals(1, loaded.getNumGuests());
        assertNotNull(loaded.getConfirmationCode());
        assertNotNull(loaded.getCreatedAt());
        assertEquals("Quiet room", loaded.getSpecialRequests());
        assertEquals(0, new BigDecimal("240.00").compareTo(loaded.getTotalPrice()));
    }

    @Test
    void findsByConfirmationCode() {
        bookingRepository.save(booking(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3),
                BookingStatus.CONFIRMED, "HBFIND01"));

        assertTrue(bookingRepository.findByConfirmationCode("HBFIND01").isPresent());
        assertTrue(bookingRepository.existsByConfirmationCode("HBFIND01"));
        assertFalse(bookingRepository.existsByConfirmationCode("HBNONE00"));
    }

    @Test
    void confirmationCodeIsUnique() {
        bookingRepository.saveAndFlush(booking(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3),
                BookingStatus.CONFIRMED, "HBDUPE01"));
        assertThrows(DataIntegrityViolationException.class, () ->
                bookingRepository.saveAndFlush(booking(2L, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6),
                        BookingStatus.CONFIRMED, "HBDUPE01")));
    }

    @Test
    void cancelledAndExpiredBookingsDoNotBlockTheRoom() {
        Long roomId = 42L;
        bookingRepository.save(booking(roomId, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 15),
                BookingStatus.CANCELLED, "HBCANC01"));
        bookingRepository.save(booking(roomId, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 15),
                BookingStatus.EXPIRED, "HBEXPR01"));

        assertFalse(bookingRepository.existsByRoomAndDateOverlap(
                roomId, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14)));

        bookingRepository.save(booking(roomId, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 15),
                BookingStatus.PENDING, "HBPEND01"));
        assertTrue(bookingRepository.existsByRoomAndDateOverlap(
                roomId, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14)));
    }
}
