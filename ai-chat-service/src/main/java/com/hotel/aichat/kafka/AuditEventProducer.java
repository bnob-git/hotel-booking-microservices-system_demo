package com.hotel.aichat.kafka;

import com.hotel.aichat.dto.AuditEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes audit events to Kafka.
 *
 * The producer is configured with acks=all, idempotence and retries, so
 * transient broker failures are retried automatically. Events that still
 * cannot be delivered are routed to a dead-letter topic instead of being
 * silently dropped.
 */
@Service
public class AuditEventProducer {

    public static final String TOPIC = "audit-events";
    public static final String DEAD_LETTER_TOPIC = "audit-events.DLT";

    private static final Logger log =
            LoggerFactory.getLogger(AuditEventProducer.class);

    private final KafkaTemplate<String, AuditEventRequest> kafkaTemplate;

    public AuditEventProducer(
            KafkaTemplate<String, AuditEventRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(AuditEventRequest event) {

        String key = event.getEventId() != null
                ? event.getEventId().toString()
                : null;

        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        sendToDeadLetter(key, event, ex);
                    }
                });
    }

    private void sendToDeadLetter(String key, AuditEventRequest event, Throwable cause) {

        log.error("Failed to publish audit event {}, routing to {}",
                key, DEAD_LETTER_TOPIC, cause);

        kafkaTemplate.send(DEAD_LETTER_TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish audit event {} to dead-letter topic: {}",
                                key, event, ex);
                    }
                });
    }
}
