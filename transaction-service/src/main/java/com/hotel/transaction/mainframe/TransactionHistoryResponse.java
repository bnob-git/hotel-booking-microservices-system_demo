package com.hotel.transaction.mainframe;

import java.util.List;

/**
 * Java view of the COBOL TXN-HIST-RESPONSE copybook read from the MQ response queue.
 * <pre>
 * 01 TXN-HIST-RESPONSE.
 *    05 RETURN-CODE    PIC X(02).
 *    05 ACCOUNT-ID     PIC X(12).
 *    05 TXN-COUNT      PIC 9(03).
 *    05 TXN-RECORD OCCURS 0 TO 100 TIMES DEPENDING ON TXN-COUNT.
 *       10 TXN-ID      PIC X(10).
 *       10 TXN-DATE    PIC X(10).
 *       10 TXN-DESC    PIC X(40).
 *       10 TXN-AMOUNT  PIC S9(11)V99 COMP-3.
 * </pre>
 */
public record TransactionHistoryResponse(
        String returnCode,
        String accountId,
        List<TransactionHistoryRecord> transactions
) {

    public static final String RC_OK = "00";
    public static final String RC_NOT_FOUND = "23";
}
