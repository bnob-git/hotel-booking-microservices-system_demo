package com.hotel.customer.mapper;

import com.hotel.customer.dto.CustomerResponse;
import com.hotel.customer.mainframe.CustomerInquiryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    private final CustomerMapper mapper = new CustomerMapper();

    @Test
    void shouldMapCopybookFieldsToJsonNamesAndStripFixedWidthPadding() {

        CustomerInquiryResponse copybook = new CustomerInquiryResponse(
                "00", "C009991   ", "Jordan Example                ", "RETAIL    ");

        CustomerResponse response = mapper.toResponse(copybook, "STUB_CICS_CUSTINQ");

        assertThat(response).isEqualTo(
                new CustomerResponse("C009991", "Jordan Example", "RETAIL", "STUB_CICS_CUSTINQ"));
    }
}
