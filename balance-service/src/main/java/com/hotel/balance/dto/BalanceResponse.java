package com.hotel.balance.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountId,
        BigDecimal available,
        BigDecimal ledger,
        String currency,
        String backend
) {
}
