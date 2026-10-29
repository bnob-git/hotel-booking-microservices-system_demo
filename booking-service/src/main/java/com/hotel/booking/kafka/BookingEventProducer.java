package com.hotel.booking.kafka;

import com.hotel.booking.dto.BookingEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public BookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${booking.events.topic:booking-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void send(BookingEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.bookingId()), event);
    }
}
