package com.hotel.customer.controller;

import com.hotel.customer.dto.CustomerResponse;
import com.hotel.customer.service.CustomerService;
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
        controllers = CustomerController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.hotel.customer.security.JwtAuthFilter.class
        )
)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnCustomerForAuthenticatedUser() throws Exception {

        when(customerService.getCustomer(eq("C009991"), anyString()))
                .thenReturn(new CustomerResponse("C009991", "Jordan Example", "RETAIL", "STUB_CICS_CUSTINQ"));

        mockMvc.perform(get("/api/customers/C009991"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("C009991"))
                .andExpect(jsonPath("$.name").value("Jordan Example"))
                .andExpect(jsonPath("$.segment").value("RETAIL"))
                .andExpect(jsonPath("$.backend").value("STUB_CICS_CUSTINQ"));
    }

    @Test
    void shouldRejectAnonymousCustomerLookup() throws Exception {

        mockMvc.perform(get("/api/customers/C009991"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void shouldReturnBadRequestForInvalidCustomerId() throws Exception {

        when(customerService.getCustomer(eq("ABC"), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: customerId must be one letter followed by 6 digits"));

        mockMvc.perform(get("/api/customers/ABC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeHealthWithoutAuth() throws Exception {

        mockMvc.perform(get("/api/customers/health"))
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
                            .requestMatchers(HttpMethod.GET, "/api/customers/health").permitAll()
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }
}
