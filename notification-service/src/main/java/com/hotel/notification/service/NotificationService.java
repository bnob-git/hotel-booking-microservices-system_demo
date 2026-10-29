package com.hotel.notification.service;

import com.hotel.notification.dto.BookingEvent;
import com.hotel.notification.entity.NotificationLog;
import com.hotel.notification.entity.NotificationStatus;
import com.hotel.notification.repository.NotificationLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    static final int MAX_ERROR_LENGTH = 1000;
    static final int MAX_LIMIT = 500;
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository repository;
    private final EmailComposer emailComposer;
    private final JavaMailSender mailSender;
    private final String from;

    public NotificationService(NotificationLogRepository repository,
                               EmailComposer emailComposer,
                               JavaMailSender mailSender,
                               @Value("${notification.mail.from:Hotel Reservations <no-reply@hotel.local>}") String from) {
        this.repository = repository;
        this.emailComposer = emailComposer;
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * Handles one booking event. Unsupported event types are ignored; every supported event results in
     * exactly one {@link NotificationLog} row (SENT / FAILED / SKIPPED), so redelivery never re-sends.
     *
     * @return the stored log, or empty when the event was ignored or already processed
     */
    public Optional<NotificationLog> handle(BookingEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("Ignoring booking event without eventId: {}", event);
            return Optional.empty();
        }
        if (!EmailComposer.supports(event.eventType())) {
            log.debug("Ignoring booking event {} of type {}", event.eventId(), event.eventType());
            return Optional.empty();
        }
        if (repository.existsByEventId(event.eventId())) {
            log.info("Booking event {} already processed, skipping", event.eventId());
            return Optional.empty();
        }

        NotificationLog entry = new NotificationLog();
        entry.setEventId(event.eventId());
        entry.setBookingId(event.bookingId());
        entry.setConfirmationCode(event.confirmationCode());
        entry.setEventType(event.eventType());
        entry.setSubject(EmailComposer.subject(event));

        String recipient = event.contactEmail();
        if (recipient == null || recipient.isBlank()) {
            log.info("Booking event {} ({}) has no contactEmail, skipping notification",
                    event.eventId(), event.eventType());
            entry.setStatus(NotificationStatus.SKIPPED);
            entry.setError("No contact email on booking");
            return save(entry);
        }

        entry.setRecipient(recipient.trim());
        try {
            send(entry.getRecipient(), emailComposer.compose(event));
            entry.setStatus(NotificationStatus.SENT);
            log.info("Sent '{}' to {}", entry.getSubject(), entry.getRecipient());
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send '{}' to {}", entry.getSubject(), entry.getRecipient(), e);
            entry.setStatus(NotificationStatus.FAILED);
            entry.setError(truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        return save(entry);
    }

    public List<NotificationLog> getRecent(Long bookingId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT)));
        return bookingId == null
                ? repository.findAllByOrderBySentAtDescIdDesc(page)
                : repository.findByBookingIdOrderBySentAtDescIdDesc(bookingId, page);
    }

    private void send(String to, BookingEmail email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(email.subject());
        helper.setText(email.text(), email.html());
        mailSender.send(message);
    }

    private Optional<NotificationLog> save(NotificationLog entry) {
        try {
            return Optional.of(repository.save(entry));
        } catch (DataIntegrityViolationException e) {
            // Concurrent redelivery of the same event: the other consumer already logged it.
            log.info("Booking event {} logged concurrently, ignoring duplicate", entry.getEventId());
            return Optional.empty();
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
