package com.hotel.notification.dto;

import com.hotel.notification.entity.NotificationLog;
import com.hotel.notification.entity.NotificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationLogResponse(
        Long id,
        UUID eventId,
        Long bookingId,
        String confirmationCode,
        String eventType,
        String recipient,
        String subject,
        NotificationStatus status,
        String error,
        LocalDateTime sentAt
) {
    public static NotificationLogResponse from(NotificationLog log) {
        return new NotificationLogResponse(
                log.getId(),
                log.getEventId(),
                log.getBookingId(),
                log.getConfirmationCode(),
                log.getEventType(),
                log.getRecipient(),
                log.getSubject(),
                log.getStatus(),
                log.getError(),
                log.getSentAt()
        );
    }
}
