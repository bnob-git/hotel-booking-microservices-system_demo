package com.hotel.booking.controller;

import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.GuestBookingRequest;
import com.hotel.booking.dto.GuestCancelRequest;
import com.hotel.booking.service.GuestBookingService;
import com.hotel.booking.service.GuestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** Public guest checkout endpoints (no JWT). */
@RestController
@RequestMapping("/api/bookings/guest")
public class GuestBookingController {

    private final GuestBookingService guestBookingService;
    private final GuestRateLimiter guestRateLimiter;

    public GuestBookingController(GuestBookingService guestBookingService, GuestRateLimiter guestRateLimiter) {
        this.guestBookingService = guestBookingService;
        this.guestRateLimiter = guestRateLimiter;
    }

    @PostMapping
    public BookingResponse create(@RequestBody GuestBookingRequest dto) {
        return guestBookingService.createGuestBooking(dto);
    }

    @GetMapping("/lookup")
    public BookingResponse lookup(@RequestParam(required = false) String code,
                                  @RequestParam(required = false) String email,
                                  HttpServletRequest request) {
        guestRateLimiter.check(clientIp(request));
        return guestBookingService.lookup(code, email);
    }

    @PostMapping("/cancel")
    public BookingResponse cancel(@RequestBody GuestCancelRequest dto, HttpServletRequest request) {
        guestRateLimiter.check(clientIp(request));
        return guestBookingService.cancel(dto);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // The last hop is the one appended by our gateway; earlier entries are client-controlled.
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
