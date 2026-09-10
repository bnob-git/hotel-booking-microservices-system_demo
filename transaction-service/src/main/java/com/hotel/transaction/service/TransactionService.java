package com.hotel.transaction.service;

import com.hotel.transaction.dto.AuditEventRequest;
import com.hotel.transaction.dto.AuditEventType;
import com.hotel.transaction.dto.TransactionsResponse;
import com.hotel.transaction.kafka.AuditEventProducer;
import com.hotel.transaction.mainframe.MainframeException;
import com.hotel.transaction.mainframe.TransactionHistoryRequest;
import com.hotel.transaction.mainframe.TransactionHistoryResponse;
import com.hotel.transaction.mainframe.TransactionMainframeClient;
import com.hotel.transaction.mapper.TransactionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TransactionService {

    private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9]{8}");
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionMainframeClient mainframeClient;
    private final TransactionMapper transactionMapper;
    private final AuditEventProducer auditEventProducer;

    public TransactionService(
            TransactionMainframeClient mainframeClient,
            TransactionMapper transactionMapper,
            AuditEventProducer auditEventProducer
    ) {
        this.mainframeClient = mainframeClient;
        this.transactionMapper = transactionMapper;
        this.auditEventProducer = auditEventProducer;
    }

    public TransactionsResponse getTransactions(String accountId, String actor) {

        if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR: accountId must be 8 digits"
            );
        }

        TransactionHistoryResponse copybook;
        try {
            copybook = mainframeClient.inquire(TransactionHistoryRequest.forAccount(accountId));
        } catch (MainframeException e) {
            throw mapFault(e);
        }

        if (TransactionHistoryResponse.RC_NOT_FOUND.equals(copybook.returnCode())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "MF_NOT_FOUND: Financial record not found"
            );
        }

        TransactionsResponse response = transactionMapper.toResponse(copybook, mainframeClient.backendName());

        publishAudit(response, actor);

        return response;
    }

    private void publishAudit(TransactionsResponse response, String actor) {

        Map<String, Object> payload = Map.of(
                "accountId", response.accountId(),
                "transactionCount", response.transactions().size(),
                "backend", response.backend()
        );

        AuditEventRequest auditEvent = new AuditEventRequest(
                UUID.randomUUID(),
                AuditEventType.TRANSACTIONS_INQUIRED,
                "transaction-service",
                actor,
                "TRANSACTIONS",
                Long.valueOf(response.accountId()),
                payload,
                "Transaction history inquiry completed"
        );

        try {
            auditEventProducer.send(auditEvent);
        } catch (Exception e) {
            log.error("Failed to send audit event for account {}", response.accountId(), e);
        }
    }

    /** Port of DefaultErrorPolicy.policy.xml fault -> HTTP status rules. */
    private static ResponseStatusException mapFault(MainframeException e) {
        HttpStatus status = switch (e.getCode()) {
            case "MF_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "MF_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, e.getCode() + ": " + e.getMessage(), e);
    }
}
