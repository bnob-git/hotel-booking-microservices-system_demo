package com.hotel.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiServiceAuthenticationFilterTest {

    private AiServiceAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {

        filter = new AiServiceAuthenticationFilter(
                "test-service-token"
        );

        chain = mock(GatewayFilterChain.class);
    }

    @Test
    void shouldForwardAuthenticatedUserAndServiceTokenToAiChatService() {

        MockServerHttpRequest request =
                MockServerHttpRequest
                        .post("/api/chat")
                        .header(
                                "X-Internal-Service-Token",
                                "attacker-token"
                        )
                        .header(
                                "X-Authenticated-User",
                                "attacker"
                        )
                        .header(
                                "X-Authenticated-Role",
                                "USER"
                        )
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "milan",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                );

        SecurityContextImpl securityContext =
                new SecurityContextImpl(authentication);

        when(chain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());

        filter.filter(exchange, chain)
                .contextWrite(
                        ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(securityContext)
                        )
                )
                .block();

        var capturedExchange =
                org.mockito.ArgumentCaptor
                        .forClass(ServerWebExchange.class);

        verify(chain).filter(capturedExchange.capture());

        HttpHeaders headers =
                capturedExchange.getValue()
                        .getRequest()
                        .getHeaders();

        assertEquals(
                "test-service-token",
                headers.getFirst("X-Internal-Service-Token")
        );

        assertEquals(
                "milan",
                headers.getFirst("X-Authenticated-User")
        );

        assertEquals(
                "ADMIN",
                headers.getFirst("X-Authenticated-Role")
        );
    }
}
