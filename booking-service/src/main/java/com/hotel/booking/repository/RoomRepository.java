package com.hotel.booking.repository;

import com.hotel.booking.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByName(String name);

    long countByAvailableTrue(); // counts available rooms

    // Rooms bookable for the whole stay in a single query; cancelled/expired bookings free the room.
    // Null filters are ignored; price filters apply to the nightly base rate.
    @Query("""
      SELECT r
      FROM Room r
      WHERE r.available = true
        AND r.price IS NOT NULL
        AND (:type IS NULL OR UPPER(r.type) = :type)
        AND (:minPrice IS NULL OR r.price >= :minPrice)
        AND (:maxPrice IS NULL OR r.price <= :maxPrice)
        AND (:guests IS NULL OR r.maxGuests IS NULL OR r.maxGuests >= :guests)
        AND NOT EXISTS (
          SELECT 1
          FROM Booking b
          WHERE b.roomId = r.id
            AND b.status NOT IN (com.hotel.booking.entity.BookingStatus.CANCELLED,
                                 com.hotel.booking.entity.BookingStatus.EXPIRED)
            AND b.checkOutDate > :checkIn
            AND b.checkInDate < :checkOut
        )
      ORDER BY r.price ASC, r.id ASC
    """)
    List<Room> findAvailableForStay(
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("guests") Integer guests,
            @Param("type") String type,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );
}
