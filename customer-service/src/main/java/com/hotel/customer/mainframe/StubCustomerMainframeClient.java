package com.hotel.customer.mainframe;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for CICS CUSTINQ, matching the BW MAINFRAME_MODE=STUB branch.
 */
@Component
@ConditionalOnProperty(prefix = "mainframe", name = "mode", havingValue = "STUB", matchIfMissing = true)
public class StubCustomerMainframeClient implements CustomerMainframeClient {

    public static final String BACKEND = "STUB_CICS_CUSTINQ";

    @Override
    public CustomerInquiryResponse inquire(CustomerInquiryRequest request) {
        return new CustomerInquiryResponse(
                CustomerInquiryResponse.RC_OK,
                request.customerId(),
                "Jordan Example",
                "RETAIL"
        );
    }

    @Override
    public String backendName() {
        return BACKEND;
    }
}
