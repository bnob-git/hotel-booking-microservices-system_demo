package com.hotel.booking.mapper;

import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.UserResponse;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.service.RoomService;
import com.hotel.booking.service.UserLookupService;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    private static final String UNKNOWN_USERNAME = "unknown";

    private final RoomService roomService;
    private final UserLookupService userLookupService;

    public BookingMapper(UserLookupService userLookupService, RoomService roomService) {
        this.userLookupService  = userLookupService;
        this.roomService = roomService;
    }

    public BookingResponse toResponse(Booking booking) {

        UserResponse user = userLookupService
                .getUserById(booking.getUserId());

        String username = user != null ? user.getUsername() : UNKNOWN_USERNAME;

        String roomName = roomService
                .getRoomById(booking.getRoomId())
                .getName();

        return BookingResponse.of(
                booking,
                username,
                roomName
        );
    }
}
