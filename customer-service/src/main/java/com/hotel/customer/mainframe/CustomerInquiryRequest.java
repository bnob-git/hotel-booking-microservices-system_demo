package com.hotel.customer.mainframe;

/**
 * Java view of the COBOL CUST-INQ-REQUEST copybook.
 * <pre>
 * 01 CUST-INQ-REQUEST.
 *    05 REQ-TYPE       PIC X(08).
 *    05 CUSTOMER-ID    PIC X(10).
 * </pre>
 */
public record CustomerInquiryRequest(String reqType, String customerId) {

    public static final String REQ_TYPE = "CUSTINQ";

    public static CustomerInquiryRequest forCustomer(String customerId) {
        return new CustomerInquiryRequest(REQ_TYPE, customerId);
    }
}
