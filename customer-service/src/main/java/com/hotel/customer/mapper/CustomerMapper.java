package com.hotel.customer.mapper;

import com.hotel.customer.dto.CustomerResponse;
import com.hotel.customer.mainframe.CustomerInquiryResponse;
import org.springframework.stereotype.Component;

/**
 * Port of the BW customer copybook-to-JSON mapping (Schemas/FinancialData.xsd Customer element).
 */
@Component
public class CustomerMapper {

    public CustomerResponse toResponse(CustomerInquiryResponse copybook, String backend) {
        return new CustomerResponse(
                trim(copybook.customerId()),
                trim(copybook.customerName()),
                trim(copybook.customerSegment()),
                backend
        );
    }

    private static String trim(String fixedWidth) {
        return fixedWidth == null ? null : fixedWidth.strip();
    }
}
