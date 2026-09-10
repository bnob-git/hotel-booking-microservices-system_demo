package com.hotel.customer.mainframe;

/**
 * Java view of the COBOL CUST-INQ-RESPONSE copybook.
 * <pre>
 * 01 CUST-INQ-RESPONSE.
 *    05 RETURN-CODE      PIC X(02).
 *    05 CUSTOMER-ID      PIC X(10).
 *    05 CUSTOMER-NAME    PIC X(30).
 *    05 CUSTOMER-SEGMENT PIC X(10).
 * </pre>
 */
public record CustomerInquiryResponse(
        String returnCode,
        String customerId,
        String customerName,
        String customerSegment
) {

    public static final String RC_OK = "00";
    public static final String RC_NOT_FOUND = "23";
}
