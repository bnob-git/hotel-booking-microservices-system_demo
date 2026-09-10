package com.hotel.account.mapper;

import com.hotel.account.dto.AccountResponse;
import com.hotel.account.mainframe.AccountInquiryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    void shouldMapCopybookFieldsToJsonNamesAndStripFixedWidthPadding() {

        AccountInquiryResponse copybook = new AccountInquiryResponse(
                "00", "10004567    ", "C009991   ", "CHECKING  ", "OPEN      ");

        AccountResponse response = mapper.toResponse(copybook, "STUB_CICS_ACCTINQ");

        assertThat(response).isEqualTo(
                new AccountResponse("10004567", "C009991", "CHECKING", "OPEN", "STUB_CICS_ACCTINQ"));
    }
}
