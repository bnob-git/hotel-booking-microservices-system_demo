package com.hotel.audit.kafka;

import com.hotel.audit.dto.AuditEventRequest;
import com.hotel.audit.entity.AuditEvent;
import com.hotel.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditService auditService;

    public AuditEventConsumer(AuditService auditService) {
        this.auditService = auditService;
    }

    @KafkaListener(topics = "audit-events", groupId = "audit-service-group")
    public void consume(AuditEventRequest request) {

        if (request.getEventId() == null) {
            // Not retryable: re-reading it would block the partition forever.
            log.warn("Discarding audit event without an eventId from {}", request.getServiceName());
            return;
        }

        AuditEvent event = new AuditEvent();

        event.setEventId(request.getEventId());
        event.setEventType(request.getEventType());
        event.setServiceName(request.getServiceName());
        event.setActor(request.getActor());
        event.setEntityType(request.getEntityType());
        event.setEntityId(request.getEntityId());
        event.setPayload(request.getPayload());
        event.setMessage(request.getMessage());

        auditService.saveEvent(event);

        log.info("Received audit event: {}", request.getEventId());
    }

}
