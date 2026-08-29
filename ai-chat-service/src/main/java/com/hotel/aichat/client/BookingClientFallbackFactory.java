package com.hotel.aichat.client;

import com.hotel.aichat.dto.BookingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    private static final Logger log =
            LoggerFactory.getLogger(BookingClientFallbackFactory.class);

    @Override
    public BookingClient create(Throwable cause) {

        return new BookingClient() {

            @Override
            public List<BookingResponse> getAllBookings() {
                log.error("Booking service call failed while loading all bookings", cause);
                return List.of();
            }

            @Override
            public List<BookingResponse> getBookingsByRoom(Long roomId) {
                log.error("Booking service call failed while loading bookings of room {}", roomId, cause);
                return List.of();
            }
        };
    }
}
