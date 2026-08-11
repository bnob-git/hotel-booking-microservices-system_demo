package com.hotel.booking.service;

import com.hotel.booking.entity.Room;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void shouldReturnRoomWhenRoomExists() {
        // Arrange
        Room room = createRoom(1L);

        when(roomRepository.findById(1L))
                .thenReturn(Optional.of(room));

        // Act
        Room result = roomService.getRoomById(1L);

        // Assert
        assertSame(room, result);
        verify(roomRepository).findById(1L);
    }

    @Test
    void shouldThrowNotFoundWhenRoomDoesNotExist() {
        // Arrange
        when(roomRepository.findById(1L))
                .thenReturn(Optional.empty());

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roomService.getRoomById(1L)
        );

        assertEquals(404, exception.getStatusCode().value());
        assertEquals("Room not found", exception.getReason());
    }

    @Test
    void shouldSaveRoomWhenNameIsAvailable() {
        // Arrange
        Room room = createRoom(null);

        when(roomRepository.findByName(room.getName()))
                .thenReturn(Optional.empty());

        when(roomRepository.save(room))
                .thenReturn(room);

        // Act
        Room result = roomService.saveRoom(room);

        // Assert
        assertSame(room, result);

        verify(roomRepository).findByName(room.getName());
        verify(roomRepository).save(room);
    }

    @Test
    void shouldRejectDuplicateRoomName() {
        // Arrange
        Room existingRoom = createRoom(1L);
        Room newRoom = createRoom(2L);

        newRoom.setName(existingRoom.getName());

        when(roomRepository.findByName(newRoom.getName()))
                .thenReturn(Optional.of(existingRoom));

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roomService.saveRoom(newRoom)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(
                "Room name already exists",
                exception.getReason()
        );

        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    void shouldNotDeleteRoomWithBookings() {
        // Arrange
        Room room = createRoom(1L);

        when(roomRepository.findById(1L))
                .thenReturn(Optional.of(room));

        when(bookingRepository.existsByRoomId(1L))
                .thenReturn(true);

        // Act + Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roomService.deleteRoom(1L)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertEquals(
                "Room has bookings and cannot be deleted",
                exception.getReason()
        );

        verify(bookingRepository).existsByRoomId(1L);
        verify(roomRepository, never()).delete(any(Room.class));
    }

    private Room createRoom(Long id) {
        Room room = new Room(
                "Test Room",
                "STANDARD",
                BigDecimal.valueOf(100),
                true
        );

        room.setId(id);

        return room;
    }
}
