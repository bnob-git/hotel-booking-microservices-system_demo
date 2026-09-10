package com.hotel.balance.mapper;

import com.hotel.balance.dto.BalanceResponse;
import com.hotel.balance.mainframe.BalanceInquiryResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Port of the BW mapper "Map packed-decimal balances to JSON decimal" in GetBalance.bwp
 * (BALANCE-INQ-RESPONSE copybook -> Balance element of FinancialData.xsd).
 */
@Component
public class BalanceMapper {

    private static final int COPYBOOK_SCALE = 2;

    public BalanceResponse toResponse(BalanceInquiryResponse copybook, String backend) {
        return new BalanceResponse(
                trim(copybook.accountId()),
                decimal(copybook.availBal()),
                decimal(copybook.ledgerBal()),
                trim(copybook.currency()),
                backend
        );
    }

    private static String trim(String fixedWidth) {
        return fixedWidth == null ? null : fixedWidth.strip();
    }

    private static BigDecimal decimal(BigDecimal packed) {
        return packed == null ? null : packed.setScale(COPYBOOK_SCALE, RoundingMode.UNNECESSARY);
    }
}
