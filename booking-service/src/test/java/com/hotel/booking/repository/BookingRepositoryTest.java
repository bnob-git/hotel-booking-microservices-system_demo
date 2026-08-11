package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class BookingRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void shouldFindBookingsByUserId() {
        // Arrange
        Long userId = 1L;

        Booking booking1 = new Booking(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                10L,
                userId
        );

        Booking booking2 = new Booking(
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(7),
                11L,
                userId
        );

        bookingRepository.save(booking1);
        bookingRepository.save(booking2);

        // Act
        List<Booking> bookings =
                bookingRepository.findByUserId(userId);

        // Assert
        assertEquals(2, bookings.size());
        assertTrue(
                bookings.stream()
                        .allMatch(b -> b.getUserId().equals(userId))
        );
    }

    @Test
    void shouldDetectOverlappingBooking() {
        // Arrange
        Long roomId = 10L;

        bookingRepository.save(
                new Booking(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 15),
                        roomId,
                        1L
                )
        );

        // Act
        boolean exists = bookingRepository.existsByRoomAndDateOverlap(
                roomId,
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 18)
        );

        // Assert
        assertTrue(exists);
    }

    @Test
    void shouldExcludeBookingWhenCheckingOverlap() {
        // Arrange
        Long roomId = 10L;

        Booking booking = bookingRepository.save(
                new Booking(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 15),
                        roomId,
                        1L
                )
        );

        // Act
        boolean exists =
                bookingRepository.existsByRoomAndDateOverlapExcludingBooking(
                        roomId,
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 15),
                        booking.getId()
                );

        // Assert
        assertFalse(exists);
    }

}
