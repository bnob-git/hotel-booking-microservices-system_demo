package com.hotel.loyalty.repository;

import com.hotel.loyalty.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against the Flyway-migrated schema (ddl-auto=validate), so it also verifies V1. */
@ActiveProfiles("test")
@DataJpaTest
class LoyaltyRepositoryTest {

    @Autowired
    private LoyaltyAccountRepository accountRepository;

    @Autowired
    private PointsTransactionRepository transactionRepository;

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Test
    void accountDefaultsAndLookup() {
        accountRepository.saveAndFlush(new LoyaltyAccount(1L, "alice"));

        LoyaltyAccount loaded = accountRepository.findByUserIdForUpdate(1L).orElseThrow();
        assertEquals(0, loaded.getPointsBalance());
        assertEquals(LoyaltyTier.BRONZE, loaded.getTier());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    void userIdIsUnique() {
        accountRepository.saveAndFlush(new LoyaltyAccount(2L, "a"));
        assertThrows(DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(new LoyaltyAccount(2L, "b")));
    }

    @Test
    void sourceEventIdIsUniqueButNullable() {
        UUID eventId = UUID.randomUUID();
        PointsTransaction first = new PointsTransaction(3L, TransactionType.EARN, 100, "x");
        first.setSourceEventId(eventId);
        transactionRepository.saveAndFlush(first);

        transactionRepository.saveAndFlush(new PointsTransaction(3L, TransactionType.REDEEM, -100, "no event"));
        transactionRepository.saveAndFlush(new PointsTransaction(3L, TransactionType.REDEEM, -100, "no event"));

        PointsTransaction dup = new PointsTransaction(3L, TransactionType.EARN, 100, "dup");
        dup.setSourceEventId(eventId);
        assertThrows(DataIntegrityViolationException.class, () -> transactionRepository.saveAndFlush(dup));
    }

    @Test
    void bookingQueries() {
        PointsTransaction earn = new PointsTransaction(4L, TransactionType.EARN, 700, "earn");
        earn.setBookingId(55L);
        transactionRepository.saveAndFlush(earn);

        assertTrue(transactionRepository.existsByBookingIdAndType(55L, TransactionType.EARN));
        assertFalse(transactionRepository.existsByBookingIdAndType(55L, TransactionType.REVERSAL));
        assertEquals(700, transactionRepository.sumPointsByBookingIdAndType(55L, TransactionType.EARN));
        assertEquals(0, transactionRepository.sumPointsByBookingIdAndType(56L, TransactionType.EARN));
        assertEquals(1, transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(4L).size());
    }

    @Test
    void redemptionPersistsAndReverses() {
        Redemption r = redemptionRepository.saveAndFlush(new Redemption(5L, 200, new BigDecimal("10.00"), "HB1"));
        r.markReversed();
        redemptionRepository.saveAndFlush(r);

        Redemption loaded = redemptionRepository.findById(r.getId()).orElseThrow();
        assertEquals(RedemptionStatus.REVERSED, loaded.getStatus());
        assertNotNull(loaded.getReversedAt());
        assertEquals(new BigDecimal("10.00"), loaded.getDiscountAmount());
        assertEquals(1, redemptionRepository.findByUserId(5L).size());
    }
}
