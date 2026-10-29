package com.hotel.booking.repository;

import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the availability query against the Flyway-migrated schema. */
@ActiveProfiles("test")
@DataJpaTest
class RoomAvailabilityRepositoryTest {

    private static final LocalDate IN = LocalDate.of(2027, 3, 10);
    private static final LocalDate OUT = LocalDate.of(2027, 3, 13);

    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;

    private Room standard;
    private Room deluxe;
    private Room suite;
    private int codeSeq;

    @BeforeEach
    void setUp() {
        standard = room("Std", "STANDARD", "60.00", true, 2);
        deluxe = room("Dlx", "DELUXE", "120.00", true, 3);
        suite = room("Ste", "SUITE", "200.00", true, null);
        room("Closed", "SUITE", "350.00", false, 4);
    }

    private Room room(String name, String type, String price, boolean available, Integer maxGuests) {
        Room r = new Room(name, type, new BigDecimal(price), available);
        r.setMaxGuests(maxGuests);
        return roomRepository.save(r);
    }

    private void book(Room room, LocalDate in, LocalDate out, BookingStatus status) {
        Booking b = new Booking(in, out, room.getId(), 1L);
        b.setStatus(status);
        b.setConfirmationCode("HBAV" + String.format("%04d", ++codeSeq));
        bookingRepository.save(b);
    }

    private List<String> search(Integer guests, String type, String min, String max) {
        return roomRepository.findAvailableForStay(IN, OUT, guests, type,
                        min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max))
                .stream().map(Room::getName).toList();
    }

    @Test
    void excludesUnavailableRoomsAndSortsByPrice() {
        assertEquals(List.of("Std", "Dlx", "Ste"), search(null, null, null, null));
    }

    @Test
    void overlappingBookingBlocksRoom() {
        book(standard, IN.minusDays(1), IN.plusDays(1), BookingStatus.CONFIRMED);   // overlaps start
        book(deluxe, OUT.minusDays(1), OUT.plusDays(2), BookingStatus.PENDING);      // overlaps end
        book(suite, IN.plusDays(1), IN.plusDays(2), BookingStatus.CHECKED_IN);       // inside

        assertEquals(List.of(), search(null, null, null, null));
    }

    @Test
    void enclosingBookingBlocksRoom() {
        book(standard, IN.minusDays(5), OUT.plusDays(5), BookingStatus.CONFIRMED);

        assertEquals(List.of("Dlx", "Ste"), search(null, null, null, null));
    }

    @Test
    void backToBackStaysAreAllowed() {
        book(standard, IN.minusDays(3), IN, BookingStatus.CONFIRMED);   // checks out on our check-in day
        book(deluxe, OUT, OUT.plusDays(2), BookingStatus.CONFIRMED);    // checks in on our check-out day

        assertEquals(List.of("Std", "Dlx", "Ste"), search(null, null, null, null));
    }

    @Test
    void cancelledAndExpiredBookingsDoNotBlock() {
        book(standard, IN, OUT, BookingStatus.CANCELLED);
        book(deluxe, IN, OUT, BookingStatus.EXPIRED);

        assertEquals(List.of("Std", "Dlx", "Ste"), search(null, null, null, null));
    }

    @Test
    void appliesTypeGuestsAndPriceFilters() {
        assertEquals(List.of("Dlx"), search(null, "DELUXE", null, null));
        // max_guests NULL means no limit
        assertEquals(List.of("Dlx", "Ste"), search(3, null, null, null));
        assertEquals(List.of("Ste"), search(4, null, null, null));
        assertEquals(List.of("Dlx", "Ste"), search(null, null, "100", null));
        assertEquals(List.of("Std", "Dlx"), search(null, null, null, "120"));
        assertEquals(List.of("Dlx"), search(2, null, "61", "199.99"));
    }
}
