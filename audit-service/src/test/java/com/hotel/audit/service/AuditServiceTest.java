package com.hotel.audit.service;

import com.hotel.audit.entity.AuditEvent;
import com.hotel.audit.entity.AuditEventType;
import com.hotel.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AuditEventWriter auditEventWriter;

    @InjectMocks
    private AuditService auditService;

    @Test
    void shouldSetTimestampAndDefaultVersionWhenSavingEvent() {

        // Arrange
        AuditEvent event = createEvent();

        when(auditEventRepository.findById(event.getEventId()))
                .thenReturn(Optional.empty());

        // Act
        AuditEvent result = auditService.saveEvent(event);

        // Assert
        assertSame(event, result);
        assertNotNull(event.getTimestamp());
        assertEquals(1, event.getVersion());

        verify(auditEventWriter).insert(event);
    }

    @Test
    void shouldPreserveExistingVersionWhenSavingEvent() {

        // Arrange
        AuditEvent event = createEvent();
        event.setVersion(2);

        when(auditEventRepository.findById(event.getEventId()))
                .thenReturn(Optional.empty());

        // Act
        auditService.saveEvent(event);

        // Assert
        assertEquals(2, event.getVersion());

        verify(auditEventWriter).insert(event);
    }

    @Test
    void shouldLimitRecentEventsTo50() {

        // Arrange
        when(auditEventRepository.findAllByOrderByTimestampDesc(
                PageRequest.of(0, 50)))
                .thenReturn(List.of());

        // Act
        auditService.getRecentEvents(100);

        // Assert
        verify(auditEventRepository)
                .findAllByOrderByTimestampDesc(PageRequest.of(0, 50));
    }

    @Test
    void shouldSkipInsertWhenEventIdIsAlreadyStored() {

        // Arrange
        AuditEvent event = createEvent();

        AuditEvent existingEvent = createEvent();

        when(auditEventRepository.findById(event.getEventId()))
                .thenReturn(Optional.of(existingEvent));

        // Act
        AuditEvent result = auditService.saveEvent(event);

        // Assert
        assertSame(existingEvent, result);

        verify(auditEventWriter, never()).insert(any());
    }

    @Test
    void shouldReturnExistingEventWhenDuplicateEventIdOccurs() {

        // Arrange
        AuditEvent event = createEvent();

        AuditEvent existingEvent = createEvent();

        // The pre-check misses because a concurrent delivery inserts in between.
        when(auditEventRepository.findById(event.getEventId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingEvent));

        doThrow(new DataIntegrityViolationException("Duplicate key"))
                .when(auditEventWriter).insert(event);

        // Act
        AuditEvent result = auditService.saveEvent(event);

        // Assert
        assertSame(existingEvent, result);

        verify(auditEventRepository, times(2)).findById(event.getEventId());
    }

    private AuditEvent createEvent() {

        AuditEvent event = new AuditEvent();

        event.setEventId(UUID.randomUUID());
        event.setEventType(AuditEventType.BOOKING_CREATED);
        event.setServiceName("booking-service");
        event.setActor("admin");
        event.setEntityType("BOOKING");
        event.setEntityId(1L);
        event.setMessage("Booking created");

        return event;
    }

    @Test
    void shouldFindEventsByTypeAndLimitTo50() {

        // Arrange
        when(auditEventRepository.findByEventTypeOrderByTimestampDesc(
                AuditEventType.BOOKING_CREATED,
                PageRequest.of(0, 50)))
                .thenReturn(List.of());

        // Act
        auditService.getEventsByType(
                AuditEventType.BOOKING_CREATED,
                100
        );

        // Assert
        verify(auditEventRepository)
                .findByEventTypeOrderByTimestampDesc(
                        AuditEventType.BOOKING_CREATED,
                        PageRequest.of(0, 50)
                );
    }
}
