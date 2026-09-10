package com.hotel.customer.mainframe;

/**
 * Boundary to the CICS program CUSTINQ (BW subprocess CallCobolCustomerInquiry).
 */
public interface CustomerMainframeClient {

    CustomerInquiryResponse inquire(CustomerInquiryRequest request);

    String backendName();
}
