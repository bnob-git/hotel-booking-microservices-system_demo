package com.hotel.loyalty.controller;

import com.hotel.loyalty.dto.LoyaltyAccountResponse;
import com.hotel.loyalty.dto.PointsTransactionResponse;
import com.hotel.loyalty.dto.RedeemRequest;
import com.hotel.loyalty.dto.RedemptionResponse;
import com.hotel.loyalty.dto.TierResponse;
import com.hotel.loyalty.security.CustomUserPrincipal;
import com.hotel.loyalty.service.LoyaltyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/me")
    public LoyaltyAccountResponse getMyAccount(Authentication authentication) {
        CustomUserPrincipal principal = principal(authentication);
        return loyaltyService.getAccount(principal.getId(), principal.getUsername());
    }

    @GetMapping("/me/transactions")
    public List<PointsTransactionResponse> getMyTransactions(Authentication authentication) {
        return loyaltyService.getTransactions(principal(authentication).getId());
    }

    // Public
    @GetMapping("/tiers")
    public List<TierResponse> getTiers() {
        return loyaltyService.getTiers();
    }

    @PostMapping("/redeem")
    public RedemptionResponse redeem(@RequestBody RedeemRequest request, Authentication authentication) {
        return loyaltyService.redeem(principal(authentication).getId(), request.points(), request.bookingReference());
    }

    private static CustomUserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)
                || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
