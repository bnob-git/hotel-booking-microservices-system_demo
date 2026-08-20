package com.hotel.aichat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class AiServiceAuthenticationFilterTest {

    private AiServiceAuthenticationFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {

        filter = new AiServiceAuthenticationFilter(
                "test-service-token"
        );

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldRejectRequestWhenServiceTokenIsInvalid()
            throws Exception {

        when(request.getHeader("X-Internal-Service-Token"))
                .thenReturn("wrong-token");

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(response)
                .setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        verify(filterChain, never())
                .doFilter(request, response);
    }

    @Test
    void shouldAllowRequestWhenServiceTokenIsValid()
            throws Exception {

        when(request.getHeader("X-Internal-Service-Token"))
                .thenReturn("test-service-token");

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(filterChain)
                .doFilter(request, response);

        verify(response, never())
                .setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
