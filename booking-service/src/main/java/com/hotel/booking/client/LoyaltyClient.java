package com.hotel.booking.client;

import com.hotel.booking.dto.LoyaltyRedeemRequest;
import com.hotel.booking.dto.LoyaltyRedemption;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "loyalty-service", url = "${services.loyalty.url}")
public interface LoyaltyClient {

    @PostMapping("/api/loyalty/internal/redeem")
    LoyaltyRedemption redeem(@RequestBody LoyaltyRedeemRequest request);

    @PostMapping("/api/loyalty/internal/redemptions/{id}/reverse")
    LoyaltyRedemption reverse(@PathVariable("id") Long id);
}
