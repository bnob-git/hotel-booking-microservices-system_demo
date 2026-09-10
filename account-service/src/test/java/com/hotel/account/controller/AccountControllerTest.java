package com.hotel.account.controller;

import com.hotel.account.dto.AccountResponse;
import com.hotel.account.service.AccountService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AccountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.hotel.account.security.JwtAuthFilter.class
        )
)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnAccountForAuthenticatedUser() throws Exception {

        when(accountService.getAccount(eq("10004567"), anyString()))
                .thenReturn(new AccountResponse("10004567", "C009991", "CHECKING", "OPEN", "STUB_CICS_ACCTINQ"));

        mockMvc.perform(get("/api/accounts/10004567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("10004567"))
                .andExpect(jsonPath("$.customerId").value("C009991"))
                .andExpect(jsonPath("$.type").value("CHECKING"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.backend").value("STUB_CICS_ACCTINQ"));
    }

    @Test
    void shouldRejectAnonymousAccountLookup() throws Exception {

        mockMvc.perform(get("/api/accounts/10004567"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnBadRequestForInvalidAccountId() throws Exception {

        when(accountService.getAccount(eq("ABC"), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: accountId must be 8 digits"));

        mockMvc.perform(get("/api/accounts/ABC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeHealthWithoutAuth() throws Exception {

        mockMvc.perform(get("/api/accounts/health"))
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
                            .requestMatchers(HttpMethod.GET, "/api/accounts/health").permitAll()
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }
}
