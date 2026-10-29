package com.hotel.booking.entity;

/**
 * Lifecycle of a booking. Transition rules are enforced by the booking state machine
 * (see docs/modernization/CONTRACTS.md); CANCELLED and EXPIRED bookings no longer block a room.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    public boolean blocksInventory() {
        return this != CANCELLED && this != EXPIRED;
    }
}
