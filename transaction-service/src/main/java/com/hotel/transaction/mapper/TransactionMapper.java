package com.hotel.transaction.mapper;

import com.hotel.transaction.dto.TransactionRecord;
import com.hotel.transaction.dto.TransactionsResponse;
import com.hotel.transaction.mainframe.TransactionHistoryRecord;
import com.hotel.transaction.mainframe.TransactionHistoryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Port of the BW mapper "Map transaction records to JSON array" (TransactionsCopybookToJson).
 */
@Component
public class TransactionMapper {

    public TransactionsResponse toResponse(TransactionHistoryResponse copybook, String backend) {
        List<TransactionRecord> records = copybook.transactions() == null
                ? List.of()
                : copybook.transactions().stream().map(TransactionMapper::toRecord).toList();
        return new TransactionsResponse(trim(copybook.accountId()), backend, records);
    }

    private static TransactionRecord toRecord(TransactionHistoryRecord r) {
        return new TransactionRecord(trim(r.txnId()), r.txnDate(), trim(r.txnDesc()), r.txnAmount());
    }

    private static String trim(String fixedWidth) {
        return fixedWidth == null ? null : fixedWidth.strip();
    }
}
