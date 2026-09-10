package com.hotel.transaction.mainframe;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic stand-in for MQ TXNHIST, matching the BW MAINFRAME_MODE=STUB branch
 * ("return three sample transactions").
 */
@Component
@ConditionalOnProperty(prefix = "mainframe", name = "mode", havingValue = "STUB", matchIfMissing = true)
public class StubTransactionMainframeClient implements TransactionMainframeClient {

    public static final String BACKEND = "STUB_MQ_TXNHIST";

    @Override
    public TransactionHistoryResponse inquire(TransactionHistoryRequest request) {
        LocalDate today = LocalDate.now();
        return new TransactionHistoryResponse(
                TransactionHistoryResponse.RC_OK,
                request.accountId(),
                List.of(
                        new TransactionHistoryRecord("T1001", today, "ACH CREDIT PAYROLL", new BigDecimal("2500.00")),
                        new TransactionHistoryRecord("T1002", today, "DEBIT CARD PURCHASE", new BigDecimal("-42.19")),
                        new TransactionHistoryRecord("T1003", today, "ATM WITHDRAWAL", new BigDecimal("-80.00"))
                )
        );
    }

    @Override
    public String backendName() {
        return BACKEND;
    }
}
