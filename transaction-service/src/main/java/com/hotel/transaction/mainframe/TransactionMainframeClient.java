package com.hotel.transaction.mainframe;

/**
 * Boundary to the COBOL program TXNHIST over IBM MQ request/reply
 * (BW subprocess CallCobolTransactionHistory: COBOL.FIN.REQ -> COBOL.FIN.RSP).
 */
public interface TransactionMainframeClient {

    TransactionHistoryResponse inquire(TransactionHistoryRequest request);

    String backendName();
}
