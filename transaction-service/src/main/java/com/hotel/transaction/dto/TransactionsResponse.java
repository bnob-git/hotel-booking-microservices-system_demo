package com.hotel.transaction.dto;

import java.util.List;

public record TransactionsResponse(
        String accountId,
        String backend,
        List<TransactionRecord> transactions
) {
}
