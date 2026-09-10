package com.hotel.balance.service;

import com.hotel.balance.dto.AuditEventRequest;
import com.hotel.balance.dto.AuditEventType;
import com.hotel.balance.dto.BalanceResponse;
import com.hotel.balance.kafka.AuditEventProducer;
import com.hotel.balance.mainframe.BalanceInquiryRequest;
import com.hotel.balance.mainframe.BalanceInquiryResponse;
import com.hotel.balance.mainframe.BalanceMainframeClient;
import com.hotel.balance.mainframe.MainframeException;
import com.hotel.balance.mapper.BalanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BalanceService {

    private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9]{8}");
    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final BalanceMainframeClient mainframeClient;
    private final BalanceMapper balanceMapper;
    private final AuditEventProducer auditEventProducer;

    public BalanceService(
            BalanceMainframeClient mainframeClient,
            BalanceMapper balanceMapper,
            AuditEventProducer auditEventProducer
    ) {
        this.mainframeClient = mainframeClient;
        this.balanceMapper = balanceMapper;
        this.auditEventProducer = auditEventProducer;
    }

    public BalanceResponse getBalance(String accountId, String actor) {

        if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR: accountId must be 8 digits"
            );
        }

        BalanceInquiryResponse copybook;
        try {
            copybook = mainframeClient.inquire(BalanceInquiryRequest.forAccount(accountId));
        } catch (MainframeException e) {
            throw mapFault(e);
        }

        if (BalanceInquiryResponse.RC_NOT_FOUND.equals(copybook.returnCode())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "MF_NOT_FOUND: Financial record not found"
            );
        }

        if (!BalanceInquiryResponse.RC_OK.equals(copybook.returnCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "MF_ERROR: Mainframe returned code " + copybook.returnCode()
            );
        }

        BalanceResponse response = balanceMapper.toResponse(copybook, mainframeClient.backendName());

        publishAudit(response, actor);

        return response;
    }

    private void publishAudit(BalanceResponse response, String actor) {

        Map<String, Object> payload = Map.of(
                "accountId", response.accountId(),
                "available", response.available(),
                "ledger", response.ledger(),
                "currency", response.currency(),
                "backend", response.backend()
        );

        AuditEventRequest auditEvent = new AuditEventRequest(
                UUID.randomUUID(),
                AuditEventType.BALANCE_INQUIRED,
                "balance-service",
                actor,
                "BALANCE",
                Long.valueOf(response.accountId()),
                payload,
                "Balance inquiry completed"
        );

        try {
            auditEventProducer.send(auditEvent).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to deliver audit event for account {}", response.accountId(), ex);
                }
            });
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
