package com.hotel.transaction.mainframe;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StubTransactionMainframeClientTest {

    private final StubTransactionMainframeClient client = new StubTransactionMainframeClient();

    @Test
    void shouldReturnThreeDeterministicTransactions() {

        TransactionHistoryResponse response = client.inquire(TransactionHistoryRequest.forAccount("10004567"));

        assertThat(response.returnCode()).isEqualTo(TransactionHistoryResponse.RC_OK);
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.transactions()).hasSize(3);
        assertThat(response.transactions().get(0).txnId()).isEqualTo("T1001");
        assertThat(response.transactions().get(0).txnDate()).isEqualTo(LocalDate.now());
        assertThat(response.transactions().get(0).txnDesc()).isEqualTo("ACH CREDIT PAYROLL");
        assertThat(response.transactions().get(0).txnAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(response.transactions().get(1).txnAmount()).isEqualByComparingTo(new BigDecimal("-42.19"));
        assertThat(response.transactions().get(2).txnAmount()).isEqualByComparingTo(new BigDecimal("-80.00"));
        assertThat(client.backendName()).isEqualTo("STUB_MQ_TXNHIST");
    }
}
