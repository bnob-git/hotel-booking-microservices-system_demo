package com.hotel.audit.entity;

public enum AuditEventType {
    BOOKING_CREATED,
    BOOKING_UPDATED,
    BOOKING_CANCELLED,
    USER_REGISTERED,
    USER_UPDATED,
    USER_DELETED,
    AI_REQUEST,
    AI_RESPONSE,
    AI_RATE_LIMITED,
    AI_ERROR,

    CUSTOMER_INQUIRED
}
