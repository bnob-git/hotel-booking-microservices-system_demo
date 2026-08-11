package com.hotel.audit.repository;

import com.hotel.audit.entity.AuditEvent;
import com.hotel.audit.entity.AuditEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AuditEventRepositoryTest {

    @Autowired
    private AuditEventRepository repository;

    @Test
    void shouldFindRecentEventsOrderedByTimestampDesc() {

        // Arrange
        AuditEvent older = createEvent(
                AuditEventType.BOOKING_CREATED,
                LocalDateTime.now().minusHours(2)
        );

        AuditEvent newer = createEvent(
                AuditEventType.USER_REGISTERED,
                LocalDateTime.now()
        );

        repository.save(older);
        repository.save(newer);

        // Act
        List<AuditEvent> events =
                repository.findAllByOrderByTimestampDesc(
                        org.springframework.data.domain.PageRequest.of(0, 10)
                );

        // Assert
        assertEquals(2, events.size());

        assertTrue(
                events.get(0).getTimestamp()
                        .isAfter(events.get(1).getTimestamp())
        );
    }

    @Test
    void shouldFindEventsByType() {

        // Arrange
        repository.save(
                createEvent(
                        AuditEventType.BOOKING_CREATED,
                        LocalDateTime.now()
                )
        );

        repository.save(
                createEvent(
                        AuditEventType.USER_REGISTERED,
                        LocalDateTime.now()
                )
        );

        // Act
        List<AuditEvent> events =
                repository.findByEventTypeOrderByTimestampDesc(
                        AuditEventType.BOOKING_CREATED,
                        org.springframework.data.domain.PageRequest.of(0, 10)
                );

        // Assert
        assertEquals(1, events.size());

        assertEquals(
                AuditEventType.BOOKING_CREATED,
                events.get(0).getEventType()
        );
    }

    private AuditEvent createEvent(
            AuditEventType type,
            LocalDateTime timestamp
    ) {

        AuditEvent event = new AuditEvent();

        event.setEventId(UUID.randomUUID());
        event.setEventType(type);
        event.setServiceName("test-service");
        event.setActor("admin");
        event.setEntityType("TEST");
        event.setEntityId(1L);
        event.setMessage("Test event");
        event.setTimestamp(timestamp);
        event.setVersion(1);

        return event;
    }
}
