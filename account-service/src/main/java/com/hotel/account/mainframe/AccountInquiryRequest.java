package com.hotel.account.mainframe;

/**
 * Java view of the COBOL ACCOUNT-INQ-REQUEST copybook.
 * <pre>
 * 01 ACCOUNT-INQ-REQUEST.
 *    05 REQ-TYPE       PIC X(08).
 *    05 ACCOUNT-ID     PIC X(12).
 * </pre>
 */
public record AccountInquiryRequest(String reqType, String accountId) {

    public static final String REQ_TYPE = "ACCTINQ";

    public static AccountInquiryRequest forAccount(String accountId) {
        return new AccountInquiryRequest(REQ_TYPE, accountId);
    }
}
