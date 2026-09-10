package com.hotel.balance.mainframe;

/**
 * Boundary to the CICS program BALINQ (BW subprocess CallCobolBalanceInquiry).
 */
public interface BalanceMainframeClient {

    BalanceInquiryResponse inquire(BalanceInquiryRequest request);

    String backendName();
}
