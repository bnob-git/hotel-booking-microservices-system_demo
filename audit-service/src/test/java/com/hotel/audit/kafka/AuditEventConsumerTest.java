package com.hotel.audit.kafka;

import com.hotel.audit.dto.AuditEventRequest;
import com.hotel.audit.entity.AuditEvent;
import com.hotel.audit.entity.AuditEventType;
import com.hotel.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditEventConsumer auditEventConsumer;

    @Test
    void shouldConvertRequestToAuditEventAndSaveIt() {

        UUID eventId = UUID.randomUUID();

        AuditEventRequest request = new AuditEventRequest();

        request.setEventId(eventId);
        request.setEventType(AuditEventType.BOOKING_CREATED);
        request.setServiceName("booking-service");
        request.setActor("admin");
        request.setEntityType("BOOKING");
        request.setEntityId(10L);
        request.setPayload(Map.of("roomId", 5));
        request.setMessage("Booking created");

        auditEventConsumer.consume(request);

        ArgumentCaptor<AuditEvent> captor =
                ArgumentCaptor.forClass(AuditEvent.class);

        verify(auditService).saveEvent(captor.capture());

        AuditEvent event = captor.getValue();

        assertEquals(eventId, event.getEventId());
        assertEquals(
                AuditEventType.BOOKING_CREATED,
                event.getEventType()
        );
        assertEquals("booking-service", event.getServiceName());
        assertEquals("admin", event.getActor());
        assertEquals("BOOKING", event.getEntityType());
        assertEquals(10L, event.getEntityId());
        assertEquals(Map.of("roomId", 5), event.getPayload());
        assertEquals("Booking created", event.getMessage());
    }
}
