package com.hotel.booking.repository;

import com.hotel.booking.entity.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class RoomRepositoryTest {

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void shouldFindRoomByName() {
        // Arrange
        Room room = new Room(
                "Test Room",
                "STANDARD",
                BigDecimal.valueOf(100),
                true
        );

        roomRepository.save(room);

        // Act
        Optional<Room> result =
                roomRepository.findByName("Test Room");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Room", result.get().getName());
    }

    @Test
    void shouldCountAvailableRooms() {
        // Arrange
        roomRepository.save(
                new Room(
                        "Available Room",
                        "STANDARD",
                        BigDecimal.valueOf(100),
                        true
                )
        );

        roomRepository.save(
                new Room(
                        "Unavailable Room",
                        "STANDARD",
                        BigDecimal.valueOf(100),
                        false
                )
        );

        // Act
        long count = roomRepository.countByAvailableTrue();

        // Assert
        assertEquals(1, count);
    }

}
