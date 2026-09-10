package com.hotel.account.mainframe;

/**
 * Boundary to the CICS program ACCTINQ (BW subprocess CallCobolAccountInquiry).
 */
public interface AccountMainframeClient {

    AccountInquiryResponse inquire(AccountInquiryRequest request);

    String backendName();
}
