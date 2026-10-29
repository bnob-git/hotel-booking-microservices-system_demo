package com.hotel.booking.controller;

import com.hotel.booking.dto.BookingResponse;
import com.hotel.booking.dto.CancelBookingRequest;
import com.hotel.booking.dto.CancellationQuote;
import com.hotel.booking.dto.PaymentRequest;
import com.hotel.booking.service.BookingLifecycleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingLifecycleController {

    private final BookingLifecycleService lifecycleService;

    public BookingLifecycleController(BookingLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    // Pay for a PENDING hold (owner or admin)
    @PostMapping("/{id}/pay")
    public BookingResponse pay(@PathVariable Long id, @RequestBody PaymentRequest request,
                               Authentication authentication) {
        return lifecycleService.pay(id, request.getPaymentMethod(), authentication);
    }

    // Soft cancel (owner or admin); DELETE /api/bookings/{id} is an alias
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id,
                                  @RequestBody(required = false) CancelBookingRequest request,
                                  Authentication authentication) {
        return lifecycleService.cancel(id, request == null ? null : request.getReason(), authentication);
    }

    // Refund preview for cancelling now (owner or admin)
    @GetMapping("/{id}/cancellation-quote")
    public CancellationQuote cancellationQuote(@PathVariable Long id) {
        return lifecycleService.getCancellationQuote(id);
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('ADMIN')")
    public BookingResponse checkIn(@PathVariable Long id, Authentication authentication) {
        return lifecycleService.checkIn(id, authentication);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public BookingResponse complete(@PathVariable Long id) {
        return lifecycleService.complete(id);
    }
}
