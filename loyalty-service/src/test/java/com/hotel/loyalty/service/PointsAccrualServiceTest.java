package com.hotel.loyalty.service;

import com.hotel.loyalty.config.LoyaltyProperties;
import com.hotel.loyalty.dto.BookingEvent;
import com.hotel.loyalty.entity.LoyaltyAccount;
import com.hotel.loyalty.entity.LoyaltyTier;
import com.hotel.loyalty.entity.PointsTransaction;
import com.hotel.loyalty.entity.TransactionType;
import com.hotel.loyalty.repository.LoyaltyAccountRepository;
import com.hotel.loyalty.repository.PointsTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.hotel.loyalty.service.PointsAccrualService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PointsAccrualServiceTest {

    private LoyaltyAccountRepository accountRepository;
    private PointsTransactionRepository transactionRepository;
    private PointsAccrualService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(LoyaltyAccountRepository.class);
        transactionRepository = mock(PointsTransactionRepository.class);
        service = new PointsAccrualService(accountRepository, transactionRepository,
                new TierPolicy(LoyaltyProperties.defaults()));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    static BookingEvent event(String type, Long userId, long bookingId, String total) {
        return new BookingEvent(1, UUID.randomUUID(), type, null, bookingId, "HB000001", null,
                userId, "alice", null, 1L, "Room 1", null, null, 2,
                total == null ? null : new BigDecimal(total), "EUR");
    }

    @Test
    void confirmedEarnsWithCurrentTierMultiplierAndPromotesTier() {
        LoyaltyAccount acc = new LoyaltyAccount(1L, "alice");
        acc.setLifetimePoints(4000);
        acc.setPointsBalance(4000);
        acc.setTier(LoyaltyTier.SILVER);
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(acc));

        assertEquals(EARNED, service.handle(event("BOOKING_CONFIRMED", 1L, 5, "800.00")));

        // 800 × 10 × 1.25
        assertEquals(14_000, acc.getPointsBalance());
        assertEquals(14_000, acc.getLifetimePoints());
        assertEquals(LoyaltyTier.GOLD, acc.getTier());

        ArgumentCaptor<PointsTransaction> tx = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(transactionRepository).save(tx.capture());
        assertEquals(TransactionType.EARN, tx.getValue().getType());
        assertEquals(10_000, tx.getValue().getPoints());
        assertEquals(5L, tx.getValue().getBookingId());
        assertEquals("HB000001", tx.getValue().getConfirmationCode());
    }

    @Test
    void confirmedCreatesAccountLazily() {
        when(accountRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.empty());

        assertEquals(EARNED, service.handle(event("BOOKING_CONFIRMED", 2L, 6, "100")));

        verify(accountRepository, times(2)).save(any(LoyaltyAccount.class));
    }

    @Test
    void guestEventsAreIgnored() {
        assertEquals(IGNORED, service.handle(event("BOOKING_CONFIRMED", null, 7, "100")));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void unknownAndUninterestingTypesAreIgnored() {
        assertEquals(IGNORED, service.handle(event("BOOKING_CREATED", 1L, 8, "100")));
        assertEquals(IGNORED, service.handle(event("BOOKING_CHECKED_IN", 1L, 8, "100")));
        assertEquals(IGNORED, service.handle(event("SOMETHING_NEW", 1L, 8, "100")));
        assertEquals(IGNORED, service.handle(null));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void duplicateEventIdIsSkipped() {
        BookingEvent e = event("BOOKING_CONFIRMED", 1L, 9, "100");
        when(transactionRepository.existsBySourceEventId(e.eventId())).thenReturn(true);

        assertEquals(DUPLICATE, service.handle(e));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void bookingEarnsOnlyOnceEvenWithNewEventId() {
        when(transactionRepository.existsByBookingIdAndType(10L, TransactionType.EARN)).thenReturn(true);

        assertEquals(DUPLICATE, service.handle(event("BOOKING_CONFIRMED", 1L, 10, "100")));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void cancelledWithoutPriorEarnIsIgnored() {
        when(transactionRepository.sumPointsByBookingIdAndType(11L, TransactionType.EARN)).thenReturn(0L);

        assertEquals(IGNORED, service.handle(event("BOOKING_CANCELLED", 1L, 11, "100")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void cancelledReversesEarnedPointsAndDemotesTier() {
        LoyaltyAccount acc = new LoyaltyAccount(1L, "alice");
        acc.setLifetimePoints(1200);
        acc.setPointsBalance(1200);
        acc.setTier(LoyaltyTier.SILVER);
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(acc));
        when(transactionRepository.sumPointsByBookingIdAndType(12L, TransactionType.EARN)).thenReturn(600L);

        assertEquals(REVERSED, service.handle(event("BOOKING_CANCELLED", 1L, 12, "60")));

        assertEquals(600, acc.getPointsBalance());
        assertEquals(600, acc.getLifetimePoints());
        assertEquals(LoyaltyTier.BRONZE, acc.getTier());
    }

    @Test
    void cancelledTwiceReversesOnce() {
        when(transactionRepository.existsByBookingIdAndType(13L, TransactionType.REVERSAL)).thenReturn(true);

        assertEquals(DUPLICATE, service.handle(event("BOOKING_CANCELLED", 1L, 13, "60")));
        verifyNoInteractions(accountRepository);
    }
}
