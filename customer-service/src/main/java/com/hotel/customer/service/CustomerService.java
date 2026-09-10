package com.hotel.customer.service;

import com.hotel.customer.dto.AuditEventRequest;
import com.hotel.customer.dto.AuditEventType;
import com.hotel.customer.dto.CustomerResponse;
import com.hotel.customer.kafka.AuditEventProducer;
import com.hotel.customer.mainframe.CustomerInquiryRequest;
import com.hotel.customer.mainframe.CustomerInquiryResponse;
import com.hotel.customer.mainframe.CustomerMainframeClient;
import com.hotel.customer.mainframe.MainframeException;
import com.hotel.customer.mapper.CustomerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CustomerService {

    private static final Pattern CUSTOMER_ID = Pattern.compile("[A-Z][0-9]{6}");
    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerMainframeClient mainframeClient;
    private final CustomerMapper customerMapper;
    private final AuditEventProducer auditEventProducer;

    public CustomerService(
            CustomerMainframeClient mainframeClient,
            CustomerMapper customerMapper,
            AuditEventProducer auditEventProducer
    ) {
        this.mainframeClient = mainframeClient;
        this.customerMapper = customerMapper;
        this.auditEventProducer = auditEventProducer;
    }

    public CustomerResponse getCustomer(String customerId, String actor) {

        if (customerId == null || !CUSTOMER_ID.matcher(customerId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR: customerId must be one letter followed by 6 digits"
            );
        }

        CustomerInquiryResponse copybook;
        try {
            copybook = mainframeClient.inquire(CustomerInquiryRequest.forCustomer(customerId));
        } catch (MainframeException e) {
            throw mapFault(e);
        }

        if (CustomerInquiryResponse.RC_NOT_FOUND.equals(copybook.returnCode())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "MF_NOT_FOUND: Financial record not found"
            );
        }

        CustomerResponse response = customerMapper.toResponse(copybook, mainframeClient.backendName());

        publishAudit(response, actor);

        return response;
    }

    private void publishAudit(CustomerResponse response, String actor) {

        Map<String, Object> payload = Map.of(
                "customerId", response.customerId(),
                "segment", response.segment(),
                "backend", response.backend()
        );

        AuditEventRequest auditEvent = new AuditEventRequest(
                UUID.randomUUID(),
                AuditEventType.CUSTOMER_INQUIRED,
                "customer-service",
                actor,
                "CUSTOMER",
                numericSuffix(response.customerId()),
                payload,
                "Customer inquiry completed"
        );

        try {
            auditEventProducer.send(auditEvent);
        } catch (Exception e) {
            log.error("Failed to send audit event for customer {}", response.customerId(), e);
        }
    }

    /** Customer ids are alphanumeric (C009991) while the audit contract carries a numeric entity id. */
    private static Long numericSuffix(String customerId) {
        String digits = customerId.replaceAll("\\D", "");
        return digits.isEmpty() ? null : Long.valueOf(digits);
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
