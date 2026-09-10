package com.hotel.account.service;

import com.hotel.account.dto.AccountResponse;
import com.hotel.account.dto.AuditEventRequest;
import com.hotel.account.dto.AuditEventType;
import com.hotel.account.kafka.AuditEventProducer;
import com.hotel.account.mainframe.AccountInquiryRequest;
import com.hotel.account.mainframe.AccountInquiryResponse;
import com.hotel.account.mainframe.AccountMainframeClient;
import com.hotel.account.mainframe.MainframeException;
import com.hotel.account.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountService {

    private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9]{8}");
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountMainframeClient mainframeClient;
    private final AccountMapper accountMapper;
    private final AuditEventProducer auditEventProducer;

    public AccountService(
            AccountMainframeClient mainframeClient,
            AccountMapper accountMapper,
            AuditEventProducer auditEventProducer
    ) {
        this.mainframeClient = mainframeClient;
        this.accountMapper = accountMapper;
        this.auditEventProducer = auditEventProducer;
    }

    public AccountResponse getAccount(String accountId, String actor) {

        if (accountId == null || !ACCOUNT_ID.matcher(accountId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR: accountId must be 8 digits"
            );
        }

        AccountInquiryResponse copybook;
        try {
            copybook = mainframeClient.inquire(AccountInquiryRequest.forAccount(accountId));
        } catch (MainframeException e) {
            throw mapFault(e);
        }

        if (AccountInquiryResponse.RC_NOT_FOUND.equals(copybook.returnCode())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "MF_NOT_FOUND: Financial record not found"
            );
        }

        if (!AccountInquiryResponse.RC_OK.equals(copybook.returnCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "MF_ERROR: Mainframe returned code " + copybook.returnCode()
            );
        }

        AccountResponse response = accountMapper.toResponse(copybook, mainframeClient.backendName());

        publishAudit(response, actor);

        return response;
    }

    private void publishAudit(AccountResponse response, String actor) {

        Map<String, Object> payload = Map.of(
                "accountId", response.accountId(),
                "customerId", response.customerId(),
                "backend", response.backend()
        );

        AuditEventRequest auditEvent = new AuditEventRequest(
                UUID.randomUUID(),
                AuditEventType.ACCOUNT_INQUIRED,
                "account-service",
                actor,
                "ACCOUNT",
                Long.valueOf(response.accountId()),
                payload,
                "Account inquiry completed"
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
