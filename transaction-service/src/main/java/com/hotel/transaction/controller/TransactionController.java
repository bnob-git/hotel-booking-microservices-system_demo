package com.hotel.transaction.controller;

import com.hotel.transaction.dto.TransactionsResponse;
import com.hotel.transaction.service.TransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "transaction-service");
    }

    @GetMapping("/{accountId}")
    public TransactionsResponse getTransactions(@PathVariable String accountId, Authentication authentication) {
        String actor = authentication == null ? "anonymous" : authentication.getName();
        return transactionService.getTransactions(accountId, actor);
    }
}
