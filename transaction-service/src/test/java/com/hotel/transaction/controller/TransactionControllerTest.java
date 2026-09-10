package com.hotel.transaction.controller;

import com.hotel.transaction.dto.TransactionRecord;
import com.hotel.transaction.dto.TransactionsResponse;
import com.hotel.transaction.service.TransactionService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TransactionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.hotel.transaction.security.JwtAuthFilter.class
        )
)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnTransactionsForAuthenticatedUser() throws Exception {

        when(transactionService.getTransactions(eq("10004567"), anyString()))
                .thenReturn(new TransactionsResponse("10004567", "STUB_MQ_TXNHIST", List.of(
                        new TransactionRecord("T1001", LocalDate.of(2026, 9, 10), "ACH CREDIT PAYROLL", new BigDecimal("2500.00")),
                        new TransactionRecord("T1002", LocalDate.of(2026, 9, 10), "DEBIT CARD PURCHASE", new BigDecimal("-42.19")))));

        mockMvc.perform(get("/api/transactions/10004567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("10004567"))
                .andExpect(jsonPath("$.backend").value("STUB_MQ_TXNHIST"))
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.transactions[0].id").value("T1001"))
                .andExpect(jsonPath("$.transactions[0].date").value("2026-09-10"))
                .andExpect(jsonPath("$.transactions[0].description").value("ACH CREDIT PAYROLL"))
                .andExpect(jsonPath("$.transactions[0].amount").value(2500.00))
                .andExpect(jsonPath("$.transactions[1].amount").value(-42.19));
    }

    @Test
    void shouldRejectAnonymousTransactionLookup() throws Exception {

        mockMvc.perform(get("/api/transactions/10004567"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnBadRequestForInvalidAccountId() throws Exception {

        when(transactionService.getTransactions(eq("ABC"), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: accountId must be 8 digits"));

        mockMvc.perform(get("/api/transactions/ABC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeHealthWithoutAuth() throws Exception {

        mockMvc.perform(get("/api/transactions/health"))
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
                            .requestMatchers(HttpMethod.GET, "/api/transactions/health").permitAll()
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }
}
