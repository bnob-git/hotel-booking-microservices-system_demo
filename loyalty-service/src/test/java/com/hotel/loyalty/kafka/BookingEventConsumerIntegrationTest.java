package com.hotel.loyalty.kafka;

import com.hotel.loyalty.entity.LoyaltyAccount;
import com.hotel.loyalty.entity.LoyaltyTier;
import com.hotel.loyalty.entity.PointsTransaction;
import com.hotel.loyalty.entity.TransactionType;
import com.hotel.loyalty.repository.LoyaltyAccountRepository;
import com.hotel.loyalty.repository.PointsTransactionRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Publishes raw JSON (no type headers, as booking-service does) to an embedded broker and asserts
 * the effect in the Flyway-migrated H2 database.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "booking-events")
@ActiveProfiles("test")
class BookingEventConsumerIntegrationTest {

    private static final String TOPIC = "booking-events";
    private static final AtomicLong IDS = new AtomicLong(1000);

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private LoyaltyAccountRepository accountRepository;

    @Autowired
    private PointsTransactionRepository transactionRepository;

    private KafkaTemplate<String, String> template;
    private DefaultKafkaProducerFactory<String, String> producerFactory;

    @BeforeEach
    void setUp() {
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

    private static String event(String eventId, String type, Long userId, long bookingId, String totalPrice) {
        return """
                {
                  "schemaVersion": 1,
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-10-29T12:00:00Z",
                  "bookingId": %d,
                  "confirmationCode": "HB%06d",
                  "status": "CONFIRMED",
                  "userId": %s,
                  "username": "alice",
                  "contactEmail": "alice@example.com",
                  "roomId": 4,
                  "roomName": "Room 201",
                  "checkInDate": "2026-12-01",
                  "checkOutDate": "2026-12-04",
                  "numGuests": 2,
                  "totalPrice": %s,
                  "currency": "EUR",
                  "someFutureField": {"nested": true}
                }
                """.formatted(eventId, type, bookingId, bookingId, userId == null ? "null" : userId, totalPrice);
    }

    private void send(long bookingId, String json) throws Exception {
        template.send(TOPIC, String.valueOf(bookingId), json).get();
    }

    private LoyaltyAccount awaitAccount(long userId, long expectedBalance) {
        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() -> {
            LoyaltyAccount a = accountRepository.findByUserId(userId).orElseThrow();
            assertEquals(expectedBalance, a.getPointsBalance());
        });
        return accountRepository.findByUserId(userId).orElseThrow();
    }

    private List<PointsTransaction> txs(long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Test
    void confirmedEventAccruesPointsAndCreatesAccount() throws Exception {
        long userId = IDS.incrementAndGet();
        long bookingId = IDS.incrementAndGet();

        send(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, bookingId, "360.00"));

        LoyaltyAccount account = awaitAccount(userId, 3600);
        assertEquals(3600, account.getLifetimePoints());
        assertEquals(LoyaltyTier.SILVER, account.getTier());
        assertEquals("alice", account.getUsername());

        List<PointsTransaction> txs = txs(userId);
        assertEquals(1, txs.size());
        assertEquals(TransactionType.EARN, txs.get(0).getType());
        assertEquals(bookingId, txs.get(0).getBookingId());
        assertEquals("HB%06d".formatted(bookingId), txs.get(0).getConfirmationCode());
    }

    @Test
    void secondBookingUsesUpgradedTierMultiplier() throws Exception {
        long userId = IDS.incrementAndGet();
        long b1 = IDS.incrementAndGet();
        long b2 = IDS.incrementAndGet();

        send(b1, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, b1, "100"));   // 1000 → SILVER
        awaitAccount(userId, 1000);
        send(b2, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, b2, "100"));   // ×1.25 = 1250

        assertEquals(2250, awaitAccount(userId, 2250).getLifetimePoints());
    }

    @Test
    void cancellationReversesEarnedPointsOnce() throws Exception {
        long userId = IDS.incrementAndGet();
        long bookingId = IDS.incrementAndGet();

        send(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, bookingId, "120.00"));
        awaitAccount(userId, 1200);

        send(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CANCELLED", userId, bookingId, "120.00"));
        send(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CANCELLED", userId, bookingId, "120.00"));

        await().atMost(Duration.ofSeconds(20)).until(() -> txs(userId).size() >= 2);
        // marker event proves the second cancel was processed (same partition, in order)
        long marker = IDS.incrementAndGet();
        send(marker, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, marker, "1"));
        LoyaltyAccount account = awaitAccount(userId, 10);

        assertEquals(10, account.getLifetimePoints());
        assertEquals(LoyaltyTier.BRONZE, account.getTier());
        assertEquals(1, txs(userId).stream().filter(t -> t.getType() == TransactionType.REVERSAL).count());
        assertEquals(-1200, txs(userId).stream().filter(t -> t.getType() == TransactionType.REVERSAL)
                .findFirst().orElseThrow().getPoints());
    }

    @Test
    void duplicateEventIsIdempotent() throws Exception {
        long userId = IDS.incrementAndGet();
        long bookingId = IDS.incrementAndGet();
        String json = event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, bookingId, "50");

        send(bookingId, json);
        send(bookingId, json);
        long marker = IDS.incrementAndGet();
        send(marker, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, marker, "1"));

        awaitAccount(userId, 510);
        assertEquals(2, txs(userId).size());
    }

    @Test
    void guestAndUnknownEventsAreIgnored() throws Exception {
        long userId = IDS.incrementAndGet();
        long guestBooking = IDS.incrementAndGet();
        long other = IDS.incrementAndGet();

        send(guestBooking, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", null, guestBooking, "500"));
        send(other, event(UUID.randomUUID().toString(), "BOOKING_CREATED", userId, other, "500"));
        send(other, event(UUID.randomUUID().toString(), "BOOKING_TELEPORTED", userId, other, "500"));
        long marker = IDS.incrementAndGet();
        send(marker, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, marker, "2"));

        awaitAccount(userId, 20);
        assertEquals(1, txs(userId).size());
        assertTrue(transactionRepository.findAll().stream().noneMatch(t -> t.getBookingId() == guestBooking));
    }

    @Test
    void malformedRecordIsSkippedWithoutBlockingLaterRecords() throws Exception {
        long userId = IDS.incrementAndGet();
        long bookingId = IDS.incrementAndGet();

        template.send(TOPIC, "poison", "{this is not json").get();
        template.send(TOPIC, "poison", "{\"eventId\":\"not-a-uuid\",\"eventType\":\"BOOKING_CONFIRMED\"}").get();
        send(bookingId, event(UUID.randomUUID().toString(), "BOOKING_CONFIRMED", userId, bookingId, "75.5"));

        assertEquals(755, awaitAccount(userId, 755).getLifetimePoints());
    }
}
