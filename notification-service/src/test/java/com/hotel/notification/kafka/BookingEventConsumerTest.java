package com.hotel.notification.kafka;

import com.hotel.notification.TestEvents;
import com.hotel.notification.dto.BookingEvent;
import com.hotel.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class BookingEventConsumerTest {

    @Test
    void shouldDelegateToNotificationService() {
        NotificationService service = mock(NotificationService.class);
        BookingEvent event = TestEvents.event("BOOKING_CONFIRMED", "jane@example.com");

        new BookingEventConsumer(service).consume(event);

        verify(service).handle(event);
    }
}
