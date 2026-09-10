package com.hotel.transaction.mainframe;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One occurrence of the TXN-RECORD group in TXN-HIST-RESPONSE.
 * <pre>
 * 10 TXN-ID          PIC X(10).
 * 10 TXN-DATE        PIC X(10).
 * 10 TXN-DESC        PIC X(40).
 * 10 TXN-AMOUNT      PIC S9(11)V99 COMP-3.
 * </pre>
 */
public record TransactionHistoryRecord(
        String txnId,
        LocalDate txnDate,
        String txnDesc,
        BigDecimal txnAmount
) {
}
