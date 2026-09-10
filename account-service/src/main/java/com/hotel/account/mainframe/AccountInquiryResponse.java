package com.hotel.account.mainframe;

/**
 * Java view of the COBOL ACCOUNT-INQ-RESPONSE copybook.
 * <pre>
 * 01 ACCOUNT-INQ-RESPONSE.
 *    05 RETURN-CODE    PIC X(02).
 *    05 ACCOUNT-ID     PIC X(12).
 *    05 CUSTOMER-ID    PIC X(10).
 *    05 ACCOUNT-TYPE   PIC X(10).
 *    05 ACCOUNT-STATUS PIC X(10).
 * </pre>
 */
public record AccountInquiryResponse(
        String returnCode,
        String accountId,
        String customerId,
        String accountType,
        String accountStatus
) {

    public static final String RC_OK = "00";
    public static final String RC_NOT_FOUND = "23";
}
