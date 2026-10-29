package com.hotel.loyalty.controller;

import com.hotel.loyalty.dto.InternalRedeemRequest;
import com.hotel.loyalty.dto.RedemptionResponse;
import com.hotel.loyalty.service.LoyaltyService;
import org.springframework.web.bind.annotation.*;

// INTERNAL ONLY: called by booking-service (Phase 2). Not routed by the api-gateway.
@RestController
@RequestMapping("/api/loyalty/internal")
public class InternalLoyaltyController {

    private final LoyaltyService loyaltyService;

    public InternalLoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @PostMapping("/redeem")
    public RedemptionResponse redeem(@RequestBody InternalRedeemRequest request) {
        return loyaltyService.redeem(request.userId(), request.points(), request.bookingReference());
    }

    @PostMapping("/redemptions/{id}/reverse")
    public RedemptionResponse reverse(@PathVariable Long id) {
        return loyaltyService.reverseRedemption(id);
    }
}
