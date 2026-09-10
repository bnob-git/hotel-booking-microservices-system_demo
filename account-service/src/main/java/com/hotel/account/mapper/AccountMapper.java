package com.hotel.account.mapper;

import com.hotel.account.dto.AccountResponse;
import com.hotel.account.mainframe.AccountInquiryResponse;
import org.springframework.stereotype.Component;

/**
 * Port of the BW mapper AccountCopybookToJson.map.xml.
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(AccountInquiryResponse copybook, String backend) {
        return new AccountResponse(
                trim(copybook.accountId()),
                trim(copybook.customerId()),
                trim(copybook.accountType()),
                trim(copybook.accountStatus()),
                backend
        );
    }

    private static String trim(String fixedWidth) {
        return fixedWidth == null ? null : fixedWidth.strip();
    }
}
