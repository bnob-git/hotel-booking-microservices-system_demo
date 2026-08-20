package com.hotel.audit.service;

import com.hotel.audit.entity.AuditEvent;
import com.hotel.audit.entity.AuditEventType;
import com.hotel.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;
    private final AuditEventWriter auditEventWriter;

    AuditService(AuditEventRepository auditEventRepository, AuditEventWriter auditEventWriter) {
        this.auditEventRepository = auditEventRepository;
        this.auditEventWriter = auditEventWriter;
    }

    /**
     * Persists an event, deduplicating on {@code eventId} (see docs/CONVENTIONS.md).
     * Re-delivering an event that is already stored is a no-op and returns the stored
     * row unchanged.
     */
    public AuditEvent saveEvent(AuditEvent event) {

        if (event.getEventId() == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        // Audit service is the source of truth for event time
        // (we never trust client-provided timestamps)
        event.setTimestamp(LocalDateTime.now());

        // Default version for event schema evolution
        // Used later when event structure changes over time
        if (event.getVersion() == null) {
            event.setVersion(1);
        }

        Optional<AuditEvent> alreadyStored = auditEventRepository.findById(event.getEventId());

        if (alreadyStored.isPresent()) {
            log.info("Skipping duplicate audit event {}", event.getEventId());
            return alreadyStored.get();
        }

        try {
            auditEventWriter.insert(event);
            return event;

        } catch (DataIntegrityViolationException e) {

            // A concurrent delivery of the same event won the race; the primary key on
            // event_id rejected this insert, so keep the stored row.
            log.info("Skipping concurrently stored duplicate audit event {}", event.getEventId());

            return auditEventRepository.findById(event.getEventId())
                    .orElseThrow(() ->
                            new IllegalStateException("Event conflict detected but not found: " + event.getEventId(), e)
                    );
        }
    }

    public List<AuditEvent> getRecentEvents(int limit) {

        limit = Math.min(limit, 50);

        return auditEventRepository.findAllByOrderByTimestampDesc( PageRequest.of(0, limit));

    }

    public List<AuditEvent> getEventsByType(AuditEventType eventType, int limit) {

        limit = Math.min(limit, 50);

        return auditEventRepository
                .findByEventTypeOrderByTimestampDesc(eventType,  PageRequest.of(0, limit));

    }
}
