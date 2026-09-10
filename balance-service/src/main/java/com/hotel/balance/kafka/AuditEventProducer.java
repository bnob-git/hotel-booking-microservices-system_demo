package com.hotel.balance.kafka;

import com.hotel.balance.dto.AuditEventRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AuditEventProducer {

    private final KafkaTemplate<String, AuditEventRequest> kafkaTemplate;

    public AuditEventProducer(
            KafkaTemplate<String, AuditEventRequest> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, AuditEventRequest>> send(AuditEventRequest event) {
        return kafkaTemplate.send("audit-events", event);
    }
}