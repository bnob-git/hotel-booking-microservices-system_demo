package com.hotel.notification.repository;

import com.hotel.notification.entity.NotificationLog;
import com.hotel.notification.entity.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real Flyway migration on H2 with ddl-auto=validate. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotificationLogRepositoryTest {

    @Autowired
    private NotificationLogRepository repository;

    @BeforeEach
    void setUp() {
        // The in-memory DB is shared with other Spring test contexts in this JVM
        repository.deleteAll();
    }

    @Test
    void shouldPersistAndFindByEventId() {
        UUID eventId = UUID.randomUUID();
        repository.saveAndFlush(log(eventId, 42L, LocalDateTime.now()));

        assertTrue(repository.existsByEventId(eventId));
        assertFalse(repository.existsByEventId(UUID.randomUUID()));
        NotificationLog found = repository.findByEventId(eventId).orElseThrow();
        assertEquals(NotificationStatus.SENT, found.getStatus());
        assertNotNull(found.getSentAt());
    }

    @Test
    void shouldEnforceUniqueEventId() {
        UUID eventId = UUID.randomUUID();
        repository.saveAndFlush(log(eventId, 42L, LocalDateTime.now()));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(log(eventId, 42L, LocalDateTime.now())));
    }

    @Test
    void shouldReturnNewestFirstAndFilterByBooking() {
        LocalDateTime now = LocalDateTime.now();
        NotificationLog older = repository.save(log(UUID.randomUUID(), 1L, now.minusHours(1)));
        NotificationLog newer = repository.save(log(UUID.randomUUID(), 2L, now));

        List<NotificationLog> all = repository.findAllByOrderBySentAtDescIdDesc(PageRequest.of(0, 10));
        assertEquals(List.of(newer.getId(), older.getId()), all.stream().map(NotificationLog::getId).toList());

        List<NotificationLog> forBooking = repository.findByBookingIdOrderBySentAtDescIdDesc(1L, PageRequest.of(0, 10));
        assertEquals(List.of(older.getId()), forBooking.stream().map(NotificationLog::getId).toList());
    }

    private static NotificationLog log(UUID eventId, Long bookingId, LocalDateTime sentAt) {
        NotificationLog log = new NotificationLog();
        log.setEventId(eventId);
        log.setBookingId(bookingId);
        log.setConfirmationCode("HB7K2QXM");
        log.setEventType("BOOKING_CONFIRMED");
        log.setRecipient("jane@example.com");
        log.setSubject("[Hotel] Booking HB7K2QXM confirmed");
        log.setStatus(NotificationStatus.SENT);
        log.setSentAt(sentAt);
        return log;
    }
}
