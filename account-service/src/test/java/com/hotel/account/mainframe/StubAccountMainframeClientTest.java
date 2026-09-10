package com.hotel.account.mainframe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubAccountMainframeClientTest {

    private final StubAccountMainframeClient client = new StubAccountMainframeClient();

    @Test
    void shouldReturnDeterministicAccountData() {

        AccountInquiryResponse response = client.inquire(AccountInquiryRequest.forAccount("10004567"));

        assertThat(response.returnCode()).isEqualTo(AccountInquiryResponse.RC_OK);
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.customerId()).isEqualTo("C009991");
        assertThat(response.accountType()).isEqualTo("CHECKING");
        assertThat(response.accountStatus()).isEqualTo("OPEN");
        assertThat(client.backendName()).isEqualTo("STUB_CICS_ACCTINQ");
    }
}
