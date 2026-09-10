package com.hotel.balance.controller;

import com.hotel.balance.dto.BalanceResponse;
import com.hotel.balance.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BalanceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.hotel.balance.security.JwtAuthFilter.class
        )
)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BalanceService balanceService;

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnBalanceForAuthenticatedUser() throws Exception {

        when(balanceService.getBalance(eq("10004567"), anyString()))
                .thenReturn(new BalanceResponse("10004567", new BigDecimal("12450.75"),
                        new BigDecimal("12600.75"), "USD", "STUB_CICS_BALINQ"));

        mockMvc.perform(get("/api/balances/10004567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("10004567"))
                .andExpect(jsonPath("$.available").value(12450.75))
                .andExpect(jsonPath("$.ledger").value(12600.75))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.backend").value("STUB_CICS_BALINQ"));
    }

    @Test
    void shouldRejectAnonymousBalanceLookup() throws Exception {

        mockMvc.perform(get("/api/balances/10004567"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnBadRequestForInvalidAccountId() throws Exception {

        when(balanceService.getBalance(eq("ABC"), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: accountId must be 8 digits"));

        mockMvc.perform(get("/api/balances/ABC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeHealthWithoutAuth() throws Exception {

        mockMvc.perform(get("/api/balances/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {

            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.GET, "/api/balances/health").permitAll()
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }
}
