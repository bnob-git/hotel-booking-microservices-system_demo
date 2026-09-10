package com.hotel.balance.mainframe;

import java.math.BigDecimal;

/**
 * Java view of the COBOL BALANCE-INQ-RESPONSE copybook.
 * <pre>
 * 01 BALANCE-INQ-RESPONSE.
 *    05 RETURN-CODE    PIC X(02).
 *    05 ACCOUNT-ID     PIC X(12).
 *    05 AVAIL-BAL      PIC S9(11)V99 COMP-3.
 *    05 LEDGER-BAL     PIC S9(11)V99 COMP-3.
 *    05 CURRENCY       PIC X(03).
 * </pre>
 * Packed-decimal (COMP-3) amounts are carried as {@link BigDecimal} with scale 2.
 */
public record BalanceInquiryResponse(
        String returnCode,
        String accountId,
        BigDecimal availBal,
        BigDecimal ledgerBal,
        String currency
) {

    public static final String RC_OK = "00";
    public static final String RC_NOT_FOUND = "23";
}
