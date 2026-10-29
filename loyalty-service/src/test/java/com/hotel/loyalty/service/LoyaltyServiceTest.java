package com.hotel.loyalty.service;

import com.hotel.loyalty.config.LoyaltyProperties;
import com.hotel.loyalty.dto.LoyaltyAccountResponse;
import com.hotel.loyalty.dto.RedemptionResponse;
import com.hotel.loyalty.entity.*;
import com.hotel.loyalty.repository.LoyaltyAccountRepository;
import com.hotel.loyalty.repository.PointsTransactionRepository;
import com.hotel.loyalty.repository.RedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoyaltyServiceTest {

    private LoyaltyAccountRepository accountRepository;
    private PointsTransactionRepository transactionRepository;
    private RedemptionRepository redemptionRepository;
    private LoyaltyService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(LoyaltyAccountRepository.class);
        transactionRepository = mock(PointsTransactionRepository.class);
        redemptionRepository = mock(RedemptionRepository.class);
        LoyaltyProperties props = LoyaltyProperties.defaults();
        service = new LoyaltyService(accountRepository, transactionRepository, redemptionRepository,
                new TierPolicy(props), props);

        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(redemptionRepository.save(any())).thenAnswer(inv -> {
            Redemption r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", 99L);
            }
            return r;
        });
    }

    private LoyaltyAccount account(long userId, long balance, long lifetime) {
        LoyaltyAccount a = new LoyaltyAccount(userId, "alice");
        a.setPointsBalance(balance);
        a.setLifetimePoints(lifetime);
        a.setTier(new TierPolicy(LoyaltyProperties.defaults()).tierFor(lifetime));
        return a;
    }

    @Test
    void getAccountReturnsEmptyBronzeForUnknownUserWithoutPersisting() {
        when(accountRepository.findByUserId(7L)).thenReturn(Optional.empty());

        LoyaltyAccountResponse res = service.getAccount(7L, "bob");

        assertEquals(7L, res.userId());
        assertEquals("bob", res.username());
        assertEquals(0, res.pointsBalance());
        assertEquals(LoyaltyTier.BRONZE, res.tier());
        assertEquals(LoyaltyTier.SILVER, res.nextTier());
        assertEquals(1000, res.pointsToNextTier());
        assertEquals(0, new BigDecimal("0.05").compareTo(res.pointValue()));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void getAccountComputesProgress() {
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account(1L, 3000, 6200)));

        LoyaltyAccountResponse res = service.getAccount(1L, "alice");

        assertEquals(LoyaltyTier.GOLD, res.tier());
        assertEquals(LoyaltyTier.PLATINUM, res.nextTier());
        assertEquals(8800, res.pointsToNextTier());
    }

    @Test
    void redeemDeductsBalanceAndRecordsRedemption() {
        LoyaltyAccount acc = account(1L, 1500, 1500);
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(acc));

        RedemptionResponse res = service.redeem(1L, 300L, " HB7K2QXM ");

        assertEquals(99L, res.redemptionId());
        assertEquals(300, res.pointsRedeemed());
        assertEquals(new BigDecimal("15.00"), res.discountAmount());
        assertEquals(1200, res.pointsBalance());
        assertEquals(RedemptionStatus.ACTIVE, res.status());
        assertEquals(1500, acc.getLifetimePoints(), "redemptions never reduce lifetime points / tier");

        ArgumentCaptor<PointsTransaction> tx = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(transactionRepository).save(tx.capture());
        assertEquals(TransactionType.REDEEM, tx.getValue().getType());
        assertEquals(-300, tx.getValue().getPoints());
        assertTrue(tx.getValue().getDescription().contains("HB7K2QXM"));
    }

    @Test
    void redeemWholeBalanceIsAllowed() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account(1L, 500, 500)));

        assertEquals(0, service.redeem(1L, 500L, null).pointsBalance());
    }

    @Test
    void redeemRejectsNonMultiplesOf100() {
        assertBadRequest(() -> service.redeem(1L, 150L, null), "multiples of 100");
        verifyNoInteractions(redemptionRepository);
    }

    @Test
    void redeemRejectsZeroNegativeAndMissingPoints() {
        assertBadRequest(() -> service.redeem(1L, 0L, null), "positive");
        assertBadRequest(() -> service.redeem(1L, -100L, null), "positive");
        assertBadRequest(() -> service.redeem(1L, null, null), "positive");
    }

    @Test
    void redeemRejectsMoreThanBalance() {
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account(1L, 200, 200)));

        assertBadRequest(() -> service.redeem(1L, 300L, null), "Insufficient points");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void redeemRejectsUserWithoutAccount() {
        when(accountRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.empty());

        assertBadRequest(() -> service.redeem(5L, 100L, null), "Insufficient points");
    }

    @Test
    void redeemRequiresUserId() {
        assertBadRequest(() -> service.redeem(null, 100L, null), "userId");
    }

    @Test
    void reverseRedemptionRestoresBalanceOnce() {
        Redemption r = new Redemption(1L, 400, new BigDecimal("20.00"), "HB1");
        ReflectionTestUtils.setField(r, "id", 10L);
        LoyaltyAccount acc = account(1L, 100, 1000);
        when(redemptionRepository.findById(10L)).thenReturn(Optional.of(r));
        when(accountRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(acc));

        RedemptionResponse first = service.reverseRedemption(10L);
        RedemptionResponse second = service.reverseRedemption(10L);

        assertEquals(RedemptionStatus.REVERSED, first.status());
        assertEquals(500, first.pointsBalance());
        assertEquals(500, second.pointsBalance());
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void reverseUnknownRedemptionIs404() {
        when(redemptionRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reverseRedemption(1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void discountIs5EurosPer100Points() {
        assertEquals(new BigDecimal("5.00"), service.discountFor(100));
        assertEquals(new BigDecimal("125.00"), service.discountFor(2500));
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable call, String messagePart) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains(messagePart), ex.getReason());
    }
}
