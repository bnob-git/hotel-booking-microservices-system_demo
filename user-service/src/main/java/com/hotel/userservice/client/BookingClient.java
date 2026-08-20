package com.hotel.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "booking-service",
        url = "${services.booking.url}",
        fallbackFactory = BookingClientFallbackFactory.class
)
public interface BookingClient {

    @GetMapping("/api/bookings/internal/users/{userId}/has-bookings")
    Boolean hasBookings(@PathVariable Long userId);
}