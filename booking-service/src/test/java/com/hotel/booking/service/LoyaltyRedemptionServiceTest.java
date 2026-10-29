package com.hotel.booking.service;

import com.hotel.booking.client.LoyaltyClient;
import com.hotel.booking.dto.LoyaltyRedeemRequest;
import com.hotel.booking.dto.LoyaltyRedemption;
import com.hotel.booking.entity.Booking;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoyaltyRedemptionServiceTest {

    private LoyaltyClient client;
    private LoyaltyRedemptionService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        client = mock(LoyaltyClient.class);
        service = new LoyaltyRedemptionService(client);
        booking = new Booking(LocalDate.now().plusDays(5), LocalDate.now().plusDays(8), 4L, 10L);
        booking.setTotalPrice(new BigDecimal("324.00"));
        booking.setConfirmationCode("HBPTS234");
    }

    private static FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "/api/loyalty/internal/redeem",
                new HashMap<>(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("redeem",
                feign.Response.builder().status(status).request(request).headers(new HashMap<>()).build());
    }

    @Test
    void noPointsMeansNoCall() {
        service.apply(booking, 10L, null);
        service.apply(booking, 10L, 0L);
        verifyNoInteractions(client);
        assertEquals(new BigDecimal("324.00"), booking.getTotalPrice());
    }

    @Test
    void redeemsPointsAndLowersTotal() {
        when(client.redeem(any())).thenReturn(new LoyaltyRedemption(55L, 500, new BigDecimal("25.00"), 1200, "ACTIVE"));

        service.apply(booking, 10L, 500L);

        verify(client).redeem(new LoyaltyRedeemRequest(10L, 500L, "HBPTS234"));
        assertEquals(new BigDecimal("299.00"), booking.getTotalPrice());
        assertEquals(new BigDecimal("25.00"), booking.getLoyaltyDiscount());
        assertEquals(500L, booking.getLoyaltyPointsRedeemed());
        assertEquals(55L, booking.getLoyaltyRedemptionId());
    }

    @Test
    void discountNeverExceedsTotal() {
        booking.setTotalPrice(new BigDecimal("20.00"));
        when(client.redeem(any())).thenReturn(new LoyaltyRedemption(56L, 1000, new BigDecimal("50.00"), 0, "ACTIVE"));

        service.apply(booking, 10L, 1000L);

        assertEquals(0, booking.getTotalPrice().signum());
        assertEquals(new BigDecimal("20.00"), booking.getLoyaltyDiscount());
    }

    @Test
    void rejectsAmountsThatAreNotMultiplesOf100() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.apply(booking, 10L, 150L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.apply(booking, 10L, -100L));
        verifyNoInteractions(client);
    }

    @Test
    void insufficientBalanceIsBadRequest() {
        when(client.redeem(any())).thenThrow(feignError(400));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.apply(booking, 10L, 100000L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(new BigDecimal("324.00"), booking.getTotalPrice());
    }

    @Test
    void loyaltyOutageIsServiceUnavailable() {
        when(client.redeem(any())).thenThrow(feignError(503));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.apply(booking, 10L, 100L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void reverseOutsideTransactionCallsLoyaltyImmediatelyAndSwallowsErrors() {
        booking.setLoyaltyRedemptionId(55L);
        when(client.reverse(56L)).thenThrow(new RuntimeException("down"));

        service.reverseAfterCommit(booking);
        service.reverseAfterCommit(List.of(56L));

        verify(client).reverse(55L);
        verify(client).reverse(56L);
    }

    @Test
    void reverseWithoutRedemptionIsNoop() {
        service.reverseAfterCommit(booking);
        verifyNoInteractions(client);
    }
}
