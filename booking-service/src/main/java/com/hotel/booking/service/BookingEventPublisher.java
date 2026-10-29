package com.hotel.booking.service;

import com.hotel.booking.dto.BookingEvent;
import com.hotel.booking.dto.BookingEventType;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.kafka.BookingEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes booking domain events to {@code booking-events}. When a transaction is active the
 * event is sent only after commit, so consumers never see bookings that were rolled back.
 * Audit events keep flowing separately through {@link com.hotel.booking.kafka.AuditEventProducer}.
 */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final BookingEventProducer bookingEventProducer;

    public BookingEventPublisher(BookingEventProducer bookingEventProducer) {
        this.bookingEventProducer = bookingEventProducer;
    }

    public void publish(BookingEventType type, Booking booking, String actor, String roomName) {
        BookingEvent event = BookingEvent.of(type, booking, actor, roomName);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(BookingEvent event) {
        try {
            bookingEventProducer.send(event);
        } catch (Exception e) {
            log.error("Failed to send {} for booking {}", event.eventType(), event.bookingId(), e);
        }
    }
}
