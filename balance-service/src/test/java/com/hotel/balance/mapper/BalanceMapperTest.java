package com.hotel.balance.mapper;

import com.hotel.balance.dto.BalanceResponse;
import com.hotel.balance.mainframe.BalanceInquiryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceMapperTest {

    private final BalanceMapper mapper = new BalanceMapper();

    @Test
    void shouldMapCopybookFieldsToJsonNamesAndStripFixedWidthPadding() {

        BalanceInquiryResponse copybook = new BalanceInquiryResponse(
                "00", "10004567    ", new BigDecimal("12450.75"), new BigDecimal("12600.75"), "USD");

        BalanceResponse response = mapper.toResponse(copybook, "STUB_CICS_BALINQ");

        assertThat(response).isEqualTo(new BalanceResponse(
                "10004567", new BigDecimal("12450.75"), new BigDecimal("12600.75"), "USD", "STUB_CICS_BALINQ"));
    }

    @Test
    void shouldNormalisePackedDecimalScaleToTwoPlaces() {

        BalanceInquiryResponse copybook = new BalanceInquiryResponse(
                "00", "10004567", new BigDecimal("100"), new BigDecimal("-42.1"), "EUR");

        BalanceResponse response = mapper.toResponse(copybook, "STUB_CICS_BALINQ");

        assertThat(response.available()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.ledger()).isEqualTo(new BigDecimal("-42.10"));
    }
}
