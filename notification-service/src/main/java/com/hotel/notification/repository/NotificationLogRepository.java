package com.hotel.notification.repository;

import com.hotel.notification.entity.NotificationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByEventId(UUID eventId);

    Optional<NotificationLog> findByEventId(UUID eventId);

    List<NotificationLog> findAllByOrderBySentAtDescIdDesc(Pageable pageable);

    List<NotificationLog> findByBookingIdOrderBySentAtDescIdDesc(Long bookingId, Pageable pageable);
}
