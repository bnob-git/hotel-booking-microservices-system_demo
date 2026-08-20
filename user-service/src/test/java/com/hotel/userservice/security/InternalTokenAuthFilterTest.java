package com.hotel.userservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalTokenAuthFilterTest {

    private static final String TOKEN = "test-only-internal-service-token";

    private InternalTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalTokenAuthFilter(TOKEN);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectInternalRequestWithoutToken() throws Exception {

        MockHttpServletResponse response = invoke("/api/users/internal/all", null);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void shouldRejectInternalRequestWithWrongToken() throws Exception {

        MockHttpServletResponse response = invoke("/api/users/internal/all", "wrong-token");

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void shouldAcceptInternalRequestWithValidToken() throws Exception {

        MockHttpServletResponse response = invoke("/api/users/internal/all", TOKEN);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        assertEquals(
                "ROLE_INTERNAL",
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );
    }

    @Test
    void shouldNotFilterNonInternalRequests() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRequestURI("/actuator/health");

        assertEquals(true, filter.shouldNotFilter(request));
    }

    @Test
    void shouldFailFastWhenTokenIsNotConfigured() {

        assertThrows(
                IllegalStateException.class,
                () -> new InternalTokenAuthFilter("")
        );
    }

    private MockHttpServletResponse invoke(String uri, String token) throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);

        if (token != null) {
            request.addHeader(InternalTokenValidator.HEADER, token);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        return response;
    }
}
