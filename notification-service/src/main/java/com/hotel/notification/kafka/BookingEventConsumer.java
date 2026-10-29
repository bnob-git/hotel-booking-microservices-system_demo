package com.hotel.notification.kafka;

import com.hotel.notification.dto.BookingEvent;
import com.hotel.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private final NotificationService notificationService;

    public BookingEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${notification.events.topic:booking-events}", groupId = "notification-service")
    public void consume(BookingEvent event) {
        notificationService.handle(event);
    }
}
