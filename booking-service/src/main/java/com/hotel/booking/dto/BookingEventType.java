package com.hotel.booking.dto;

/**
 * Domain events published to the {@code booking-events} topic.
 * Consumers (loyalty-service, notification-service) must ignore types they do not handle.
 */
public enum BookingEventType {
    BOOKING_CREATED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_CHECKED_IN
}
