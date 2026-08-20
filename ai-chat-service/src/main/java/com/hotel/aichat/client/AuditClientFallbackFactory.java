package com.hotel.aichat.client;

import com.hotel.aichat.dto.AuditEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditClientFallbackFactory implements FallbackFactory<AuditClient> {

    private static final Logger log =
            LoggerFactory.getLogger(AuditClientFallbackFactory.class);

    @Override
    public AuditClient create(Throwable cause) {

        return new AuditClient() {

            @Override
            public List<AuditEventResponse> getRecentEvents(int limit) {
                log.error("Audit service call failed while loading recent events", cause);
                return List.of();
            }

            @Override
            public List<AuditEventResponse> getEventsByType(String eventType, int limit) {
                log.error("Audit service call failed while loading events of type {}", eventType, cause);
                return List.of();
            }
        };
    }
}
