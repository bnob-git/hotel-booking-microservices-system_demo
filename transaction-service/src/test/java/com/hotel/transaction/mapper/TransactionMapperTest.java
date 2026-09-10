package com.hotel.transaction.mapper;

import com.hotel.transaction.dto.TransactionRecord;
import com.hotel.transaction.dto.TransactionsResponse;
import com.hotel.transaction.mainframe.TransactionHistoryRecord;
import com.hotel.transaction.mainframe.TransactionHistoryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper();

    @Test
    void shouldMapCopybookRecordsToJsonNamesAndStripFixedWidthPadding() {

        LocalDate date = LocalDate.of(2026, 9, 10);
        TransactionHistoryResponse copybook = new TransactionHistoryResponse(
                "00", "10004567    ",
                List.of(new TransactionHistoryRecord("T1001     ", date, "ACH CREDIT PAYROLL                      ", new BigDecimal("2500.00"))));

        TransactionsResponse response = mapper.toResponse(copybook, "STUB_MQ_TXNHIST");

        assertThat(response).isEqualTo(new TransactionsResponse("10004567", "STUB_MQ_TXNHIST",
                List.of(new TransactionRecord("T1001", date, "ACH CREDIT PAYROLL", new BigDecimal("2500.00")))));
    }

    @Test
    void shouldMapNullRecordsToEmptyList() {

        TransactionsResponse response = mapper.toResponse(
                new TransactionHistoryResponse("00", "10004567", null), "STUB_MQ_TXNHIST");

        assertThat(response.transactions()).isEmpty();
    }
}
