package com.hotel.balance.mainframe;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StubBalanceMainframeClientTest {

    private final StubBalanceMainframeClient client = new StubBalanceMainframeClient();

    @Test
    void shouldReturnDeterministicBalanceData() {

        BalanceInquiryResponse response = client.inquire(BalanceInquiryRequest.forAccount("10004567"));

        assertThat(response.returnCode()).isEqualTo(BalanceInquiryResponse.RC_OK);
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.availBal()).isEqualByComparingTo(new BigDecimal("12450.75"));
        assertThat(response.ledgerBal()).isEqualByComparingTo(new BigDecimal("12600.75"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(client.backendName()).isEqualTo("STUB_CICS_BALINQ");
    }
}
