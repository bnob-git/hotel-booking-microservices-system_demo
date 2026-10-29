package com.hotel.notification.service;

import com.hotel.notification.TestEvents;
import com.hotel.notification.config.EmailTemplateConfig;
import com.hotel.notification.dto.BookingEvent;
import com.hotel.notification.entity.NotificationLog;
import com.hotel.notification.entity.NotificationStatus;
import com.hotel.notification.repository.NotificationLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationLogRepository repository;
    private JavaMailSender mailSender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationLogRepository.class);
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        when(repository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        EmailComposer composer = new EmailComposer(new EmailTemplateConfig().emailTemplateEngine(), "http://localhost:4200");
        service = new NotificationService(repository, composer, mailSender, "Hotel <no-reply@hotel.local>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOOKING_CONFIRMED", "BOOKING_CANCELLED", "BOOKING_CHECKED_IN"})
    void shouldSendHtmlAndTextEmailAndLogSent(String type) throws Exception {
        BookingEvent event = TestEvents.event(type, "jane@example.com");

        NotificationLog logged = service.handle(event).orElseThrow();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertEquals(EmailComposer.subject(event), message.getSubject());
        assertEquals("jane@example.com", message.getAllRecipients()[0].toString());
        assertTrue(message.getFrom()[0].toString().contains("no-reply@hotel.local"));
        message.saveChanges();
        String raw = new String(message.getInputStream().readAllBytes());
        assertNotNull(raw);
        assertTrue(message.getContent() instanceof MimeMultipart);

        assertEquals(NotificationStatus.SENT, logged.getStatus());
        assertEquals(event.eventId(), logged.getEventId());
        assertEquals(42L, logged.getBookingId());
        assertEquals("HB7K2QXM", logged.getConfirmationCode());
        assertEquals(type, logged.getEventType());
        assertEquals("jane@example.com", logged.getRecipient());
        assertEquals(EmailComposer.subject(event), logged.getSubject());
        assertNull(logged.getError());
    }

    @Test
    void shouldIgnoreUnsupportedEventTypes() {
        assertTrue(service.handle(TestEvents.event("BOOKING_CREATED", "jane@example.com")).isEmpty());
        assertTrue(service.handle(TestEvents.event("SOMETHING_NEW", "jane@example.com")).isEmpty());
        assertTrue(service.handle(TestEvents.event(null, "jane@example.com")).isEmpty());

        verifyNoInteractions(mailSender);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldIgnoreEventWithoutId() {
        assertTrue(service.handle(TestEvents.event(null, "BOOKING_CONFIRMED", "jane@example.com")).isEmpty());
        assertTrue(service.handle(null).isEmpty());
        verifyNoInteractions(mailSender, repository);
    }

    @Test
    void shouldBeIdempotentOnEventId() {
        BookingEvent event = TestEvents.event("BOOKING_CONFIRMED", "jane@example.com");
        when(repository.existsByEventId(event.eventId())).thenReturn(true);

        assertTrue(service.handle(event).isEmpty());

        verifyNoInteractions(mailSender);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldSkipAndLogWhenNoContactEmail() {
        for (String email : new String[]{null, "  "}) {
            NotificationLog logged = service.handle(TestEvents.event("BOOKING_CONFIRMED", email)).orElseThrow();
            assertEquals(NotificationStatus.SKIPPED, logged.getStatus());
            assertNull(logged.getRecipient());
            assertEquals("No contact email on booking", logged.getError());
        }
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldLogFailedWhenSmtpFails() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        NotificationLog logged = service.handle(TestEvents.event("BOOKING_CANCELLED", "jane@example.com")).orElseThrow();

        assertEquals(NotificationStatus.FAILED, logged.getStatus());
        assertTrue(logged.getError().contains("SMTP down"));
    }

    @Test
    void shouldTruncateLongErrors() {
        doThrow(new MailSendException("x".repeat(5000))).when(mailSender).send(any(MimeMessage.class));

        NotificationLog logged = service.handle(TestEvents.event("BOOKING_CANCELLED", "jane@example.com")).orElseThrow();

        assertEquals(NotificationService.MAX_ERROR_LENGTH, logged.getError().length());
    }

    @Test
    void shouldIgnoreConcurrentDuplicateInsert() {
        when(repository.save(any(NotificationLog.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertEquals(Optional.empty(), service.handle(TestEvents.event("BOOKING_CONFIRMED", "jane@example.com")));
    }

    @Test
    void shouldClampLimitAndFilterByBooking() {
        NotificationLog log = new NotificationLog();
        when(repository.findAllByOrderBySentAtDescIdDesc(any(Pageable.class))).thenReturn(List.of(log));
        when(repository.findByBookingIdOrderBySentAtDescIdDesc(eq(42L), any(Pageable.class))).thenReturn(List.of());

        assertEquals(List.of(log), service.getRecent(null, 10_000));
        assertEquals(List.of(), service.getRecent(42L, 0));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderBySentAtDescIdDesc(page.capture());
        assertEquals(NotificationService.MAX_LIMIT, page.getValue().getPageSize());
        verify(repository).findByBookingIdOrderBySentAtDescIdDesc(eq(42L), page.capture());
        assertEquals(1, page.getValue().getPageSize());
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
