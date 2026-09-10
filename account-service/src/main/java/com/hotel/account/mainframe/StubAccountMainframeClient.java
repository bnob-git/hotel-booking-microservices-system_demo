package com.hotel.account.mainframe;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for CICS ACCTINQ, matching the BW MAINFRAME_MODE=STUB branch.
 */
@Component
@ConditionalOnProperty(prefix = "mainframe", name = "mode", havingValue = "STUB", matchIfMissing = true)
public class StubAccountMainframeClient implements AccountMainframeClient {

    public static final String BACKEND = "STUB_CICS_ACCTINQ";

    @Override
    public AccountInquiryResponse inquire(AccountInquiryRequest request) {
        return new AccountInquiryResponse(
                AccountInquiryResponse.RC_OK,
                request.accountId(),
                "C009991",
                "CHECKING",
                "OPEN"
        );
    }

    @Override
    public String backendName() {
        return BACKEND;
    }
}
