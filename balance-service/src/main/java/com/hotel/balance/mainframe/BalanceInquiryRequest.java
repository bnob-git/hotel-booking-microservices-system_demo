package com.hotel.balance.mainframe;

/**
 * Java view of the COBOL BALANCE-INQ-REQUEST copybook built by CallCobolBalanceInquiry.
 * <pre>
 * 01 BALANCE-INQ-REQUEST.
 *    05 REQ-TYPE       PIC X(08).
 *    05 ACCOUNT-ID     PIC X(12).
 * </pre>
 */
public record BalanceInquiryRequest(String reqType, String accountId) {

    public static final String REQ_TYPE = "BALINQ";

    public static BalanceInquiryRequest forAccount(String accountId) {
        return new BalanceInquiryRequest(REQ_TYPE, accountId);
    }
}
