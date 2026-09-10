package com.hotel.account.controller;

import com.hotel.account.dto.AccountResponse;
import com.hotel.account.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "account-service");
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId, Authentication authentication) {
        String actor = authentication == null ? "anonymous" : authentication.getName();
        return accountService.getAccount(accountId, actor);
    }
}
