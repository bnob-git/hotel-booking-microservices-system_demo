package com.hotel.loyalty.controller;

import com.hotel.loyalty.TestTokens;
import com.hotel.loyalty.config.SecurityConfig;
import com.hotel.loyalty.dto.LoyaltyAccountResponse;
import com.hotel.loyalty.dto.PointsTransactionResponse;
import com.hotel.loyalty.dto.RedemptionResponse;
import com.hotel.loyalty.dto.TierResponse;
import com.hotel.loyalty.entity.LoyaltyTier;
import com.hotel.loyalty.entity.RedemptionStatus;
import com.hotel.loyalty.entity.TransactionType;
import com.hotel.loyalty.exception.GlobalExceptionHandler;
import com.hotel.loyalty.security.JwtService;
import com.hotel.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {LoyaltyController.class, InternalLoyaltyController.class})
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=" + TestTokens.SECRET)
class LoyaltyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoyaltyService loyaltyService;

    private static final String ALICE = TestTokens.bearer(3L, "alice", "USER");

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/loyalty/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/loyalty/me/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/loyalty/redeem").contentType(APPLICATION_JSON).content("{\"points\":100}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(loyaltyService);
    }

    @Test
    void invalidOrForeignTokenIsRejected() throws Exception {
        String forged = "Bearer " + TestTokens.token(3L, "alice", "USER",
                "another-secret-that-is-also-32-bytes-long!!", 60_000);

        mockMvc.perform(get("/api/loyalty/me").header("Authorization", forged))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/loyalty/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meUsesUserIdFromJwt() throws Exception {
        when(loyaltyService.getAccount(3L, "alice")).thenReturn(new LoyaltyAccountResponse(
                3L, "alice", 4500, 4500, LoyaltyTier.SILVER, LoyaltyTier.GOLD, 500, new BigDecimal("0.05")));

        mockMvc.perform(get("/api/loyalty/me").header("Authorization", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.pointsBalance").value(4500))
                .andExpect(jsonPath("$.lifetimePoints").value(4500))
                .andExpect(jsonPath("$.tier").value("SILVER"))
                .andExpect(jsonPath("$.nextTier").value("GOLD"))
                .andExpect(jsonPath("$.pointsToNextTier").value(500))
                .andExpect(jsonPath("$.pointValue").value(0.05));
    }

    @Test
    void transactionsForCurrentUser() throws Exception {
        when(loyaltyService.getTransactions(3L)).thenReturn(List.of(new PointsTransactionResponse(
                1L, TransactionType.EARN, 3600, 42L, "HB7K2QXM", "Earned", LocalDateTime.of(2026, 10, 29, 12, 0))));

        mockMvc.perform(get("/api/loyalty/me/transactions").header("Authorization", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("EARN"))
                .andExpect(jsonPath("$[0].points").value(3600))
                .andExpect(jsonPath("$[0].bookingId").value(42))
                .andExpect(jsonPath("$[0].confirmationCode").value("HB7K2QXM"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void tiersArePublic() throws Exception {
        when(loyaltyService.getTiers()).thenReturn(List.of(
                new TierResponse(LoyaltyTier.BRONZE, 0, new BigDecimal("1.00"), List.of("perk"))));

        mockMvc.perform(get("/api/loyalty/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tier").value("BRONZE"))
                .andExpect(jsonPath("$[0].minLifetimePoints").value(0))
                .andExpect(jsonPath("$[0].earnMultiplier").value(1.0))
                .andExpect(jsonPath("$[0].perks[0]").value("perk"));
    }

    @Test
    void redeemForCurrentUser() throws Exception {
        when(loyaltyService.redeem(3L, 200L, "HB7K2QXM")).thenReturn(
                new RedemptionResponse(5L, 200, new BigDecimal("10.00"), 4300, RedemptionStatus.ACTIVE));

        mockMvc.perform(post("/api/loyalty/redeem")
                        .header("Authorization", ALICE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"points\":200,\"bookingReference\":\"HB7K2QXM\",\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptionId").value(5))
                .andExpect(jsonPath("$.pointsRedeemed").value(200))
                .andExpect(jsonPath("$.discountAmount").value(10.0))
                .andExpect(jsonPath("$.pointsBalance").value(4300));

        // userId in the body is ignored; the JWT principal wins.
        verify(loyaltyService).redeem(3L, 200L, "HB7K2QXM");
    }

    @Test
    void redeemValidationErrorsAre400WithMessage() throws Exception {
        when(loyaltyService.redeem(eq(3L), eq(150L), isNull())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be redeemed in multiples of 100"));

        mockMvc.perform(post("/api/loyalty/redeem")
                        .header("Authorization", ALICE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"points\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Points must be redeemed in multiples of 100"));
    }

    @Test
    void redeemWithMalformedBodyIs400() throws Exception {
        mockMvc.perform(post("/api/loyalty/redeem")
                        .header("Authorization", ALICE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"points\":\"lots\"}"))
                .andExpect(status().isBadRequest());
        verify(loyaltyService, never()).redeem(anyLong(), anyLong(), any());
    }

    @Test
    void internalRedeemNeedsNoJwt() throws Exception {
        when(loyaltyService.redeem(7L, 100L, "HB000001")).thenReturn(
                new RedemptionResponse(1L, 100, new BigDecimal("5.00"), 0, RedemptionStatus.ACTIVE));

        mockMvc.perform(post("/api/loyalty/internal/redeem")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":7,\"points\":100,\"bookingReference\":\"HB000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(5.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void internalReverseNeedsNoJwt() throws Exception {
        when(loyaltyService.reverseRedemption(1L)).thenReturn(
                new RedemptionResponse(1L, 100, new BigDecimal("5.00"), 100, RedemptionStatus.REVERSED));

        mockMvc.perform(post("/api/loyalty/internal/redemptions/1/reverse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.pointsBalance").value(100));
    }

    @Test
    void internalReverseUnknownIs404() throws Exception {
        when(loyaltyService.reverseRedemption(404L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Redemption not found"));

        mockMvc.perform(post("/api/loyalty/internal/redemptions/404/reverse"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Redemption not found"));
    }
}
