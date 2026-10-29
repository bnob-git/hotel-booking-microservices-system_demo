package com.hotel.loyalty.kafka;

import com.hotel.loyalty.dto.BookingEvent;
import com.hotel.loyalty.service.PointsAccrualService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final PointsAccrualService accrualService;

    public BookingEventConsumer(PointsAccrualService accrualService) {
        this.accrualService = accrualService;
    }

    @KafkaListener(topics = "${loyalty.events.topic:booking-events}", groupId = "loyalty-service")
    public void consume(BookingEvent event) {
        PointsAccrualService.Outcome outcome = accrualService.handle(event);
        log.debug("booking-event {} {} -> {}", event.eventType(), event.eventId(), outcome);
    }
}
