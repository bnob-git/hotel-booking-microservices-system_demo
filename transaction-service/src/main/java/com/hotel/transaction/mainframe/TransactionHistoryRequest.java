package com.hotel.transaction.mainframe;

/**
 * Java view of the COBOL TXN-HIST-REQUEST copybook placed on the MQ request queue.
 * <pre>
 * 01 TXN-HIST-REQUEST.
 *    05 REQ-TYPE       PIC X(08).
 *    05 ACCOUNT-ID     PIC X(12).
 * </pre>
 */
public record TransactionHistoryRequest(String reqType, String accountId) {

    public static final String REQ_TYPE = "TXNHIST";

    public static TransactionHistoryRequest forAccount(String accountId) {
        return new TransactionHistoryRequest(REQ_TYPE, accountId);
    }
}
