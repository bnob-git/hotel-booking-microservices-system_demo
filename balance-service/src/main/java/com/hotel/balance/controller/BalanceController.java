package com.hotel.balance.controller;

import com.hotel.balance.dto.BalanceResponse;
import com.hotel.balance.service.BalanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "balance-service");
    }

    @GetMapping("/{accountId}")
    public BalanceResponse getBalance(@PathVariable String accountId, Authentication authentication) {
        String actor = authentication == null ? "anonymous" : authentication.getName();
        return balanceService.getBalance(accountId, actor);
    }
}
