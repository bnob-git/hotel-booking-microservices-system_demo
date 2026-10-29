package com.hotel.notification.controller;

import com.hotel.notification.dto.NotificationLogResponse;
import com.hotel.notification.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ADMIN: most recent notifications first, optionally for one booking
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<NotificationLogResponse> getNotifications(@RequestParam(required = false) Long bookingId,
                                                          @RequestParam(defaultValue = "50") int limit) {
        return notificationService.getRecent(bookingId, limit)
                .stream()
                .map(NotificationLogResponse::from)
                .toList();
    }
}
