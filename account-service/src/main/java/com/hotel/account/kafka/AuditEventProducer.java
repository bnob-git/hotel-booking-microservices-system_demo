package com.hotel.account.kafka;

import com.hotel.account.dto.AuditEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventProducer.class);

    private final KafkaTemplate<String, AuditEventRequest> kafkaTemplate;

    public AuditEventProducer(
            KafkaTemplate<String, AuditEventRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(AuditEventRequest event) {
        kafkaTemplate.send("audit-events", event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish audit event {} for {} {}",
                        event.getEventType(), event.getEntityType(), event.getEntityId(), ex);
            }
        });
    }
}
