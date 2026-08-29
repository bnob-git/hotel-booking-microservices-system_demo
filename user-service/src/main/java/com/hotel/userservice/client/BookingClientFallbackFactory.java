package com.hotel.userservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    private static final Logger log =
            LoggerFactory.getLogger(BookingClientFallbackFactory.class);

    @Override
    public BookingClient create(Throwable cause) {

        return userId -> {
            log.error("Booking service call failed for user {}", userId, cause);
            return null;
        };
    }
}
