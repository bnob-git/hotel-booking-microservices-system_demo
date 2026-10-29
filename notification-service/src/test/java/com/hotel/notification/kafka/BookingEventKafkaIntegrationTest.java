package com.hotel.notification.kafka;

import com.hotel.notification.entity.NotificationLog;
import com.hotel.notification.entity.NotificationStatus;
import com.hotel.notification.repository.NotificationLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end through a real (embedded) Kafka broker: raw JSON exactly as booking-service publishes it
 * (no type headers) → consumer → email sent via JavaMailSender (mocked) → NotificationLog.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"
})
@EmbeddedKafka(partitions = 1, topics = "booking-events")
@ActiveProfiles("test")
@DirtiesContext
class BookingEventKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private NotificationLogRepository repository;

    @MockBean
    private JavaMailSender mailSender;

    private KafkaTemplate<String, String> template;
    private DefaultKafkaProducerFactory<String, String> producerFactory;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerFactory = new DefaultKafkaProducerFactory<>(props);
        template = new KafkaTemplate<>(producerFactory);
    }

    @AfterEach
    void tearDown() {
        producerFactory.destroy();
    }

    @Test
    void shouldSendEmailPerSupportedTypeSkipMalformedAndStayIdempotent() throws Exception {
        UUID confirmed = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        UUID checkedIn = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        UUID noEmail = UUID.randomUUID();

        // A poison record first: must be skipped without blocking the partition
        send("{not valid json");
        send(json(created, "BOOKING_CREATED", "\"jane@example.com\""));
        send(json(confirmed, "BOOKING_CONFIRMED", "\"jane@example.com\""));
        send(json(confirmed, "BOOKING_CONFIRMED", "\"jane@example.com\""));   // redelivery
        send(json(cancelled, "BOOKING_CANCELLED", "\"jane@example.com\""));
        send(json(checkedIn, "BOOKING_CHECKED_IN", "\"jane@example.com\""));
        send(json(noEmail, "BOOKING_CONFIRMED", "null"));

        await().atMost(Duration.ofSeconds(30)).until(() -> repository.count() == 4);
        // give a duplicate time to (wrongly) show up
        Thread.sleep(1000);

        assertEquals(4, repository.count());
        assertEquals(NotificationStatus.SENT, status(confirmed));
        assertEquals(NotificationStatus.SENT, status(cancelled));
        assertEquals(NotificationStatus.SENT, status(checkedIn));
        assertEquals(NotificationStatus.SKIPPED, status(noEmail));
        assertFalse(repository.existsByEventId(created));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(3)).send(captor.capture());
        List<String> subjects = captor.getAllValues().stream().map(BookingEventKafkaIntegrationTest::subject).toList();
        assertEquals(List.of(
                "[Hotel] Booking HB7K2QXM confirmed",
                "[Hotel] Booking HB7K2QXM cancelled",
                "[Hotel] Booking HB7K2QXM checked in"
        ), subjects);
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    private NotificationStatus status(UUID eventId) {
        return repository.findByEventId(eventId).map(NotificationLog::getStatus).orElse(null);
    }

    private void send(String payload) throws Exception {
        template.send("booking-events", "42", payload).get();
    }

    private static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Matches the booking-service JSON (BookingEvent v1) plus an unknown field that must be ignored. */
    private static String json(UUID eventId, String type, String contactEmailJson) {
        return """
                {"schemaVersion":1,"eventId":"%s","eventType":"%s","occurredAt":"2026-10-29T12:00:00Z",
                 "bookingId":42,"confirmationCode":"HB7K2QXM","status":"CONFIRMED","userId":null,
                 "username":"Jane Guest","contactEmail":%s,"roomId":4,"roomName":"Room 201",
                 "checkInDate":"2026-12-01","checkOutDate":"2026-12-04","numGuests":2,"totalPrice":360.00,
                 "currency":"EUR","someFutureField":{"nested":true}}
                """.formatted(eventId, type, contactEmailJson);
    }
}
