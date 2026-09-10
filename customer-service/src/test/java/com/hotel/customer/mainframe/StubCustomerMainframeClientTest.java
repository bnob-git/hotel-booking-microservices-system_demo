package com.hotel.customer.mainframe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubCustomerMainframeClientTest {

    private final StubCustomerMainframeClient client = new StubCustomerMainframeClient();

    @Test
    void shouldReturnDeterministicCustomerData() {

        CustomerInquiryResponse response = client.inquire(CustomerInquiryRequest.forCustomer("C009991"));

        assertThat(response.returnCode()).isEqualTo(CustomerInquiryResponse.RC_OK);
        assertThat(response.customerId()).isEqualTo("C009991");
        assertThat(response.customerName()).isEqualTo("Jordan Example");
        assertThat(response.customerSegment()).isEqualTo("RETAIL");
        assertThat(client.backendName()).isEqualTo("STUB_CICS_CUSTINQ");
    }
}
