package com.hotel.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRecord(
        String id,
        LocalDate date,
        String description,
        BigDecimal amount
) {
}
