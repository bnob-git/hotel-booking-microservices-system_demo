package com.hotel.balance.mainframe;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Deterministic stand-in for CICS BALINQ, matching the BW MAINFRAME_MODE=STUB branch
 * ("Return packed-decimal-like sample balances").
 */
@Component
@ConditionalOnProperty(prefix = "mainframe", name = "mode", havingValue = "STUB", matchIfMissing = true)
public class StubBalanceMainframeClient implements BalanceMainframeClient {

    public static final String BACKEND = "STUB_CICS_BALINQ";

    @Override
    public BalanceInquiryResponse inquire(BalanceInquiryRequest request) {
        return new BalanceInquiryResponse(
                BalanceInquiryResponse.RC_OK,
                request.accountId(),
                new BigDecimal("12450.75"),
                new BigDecimal("12600.75"),
                "USD"
        );
    }

    @Override
    public String backendName() {
        return BACKEND;
    }
}
