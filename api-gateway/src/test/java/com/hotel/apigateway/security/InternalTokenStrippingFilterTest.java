package com.hotel.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalTokenStrippingFilterTest {

    private InternalTokenStrippingFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new InternalTokenStrippingFilter();
        chain = mock(GatewayFilterChain.class);

        when(chain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    void shouldRemoveClientSuppliedInternalToken() {

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users/internal/all")
                .header(
                        InternalTokenStrippingFilter.INTERNAL_TOKEN_HEADER,
                        "attacker-token"
                )
                .build();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertFalse(
                forwardedExchange().getRequest()
                        .getHeaders()
                        .containsKey(
                                InternalTokenStrippingFilter.INTERNAL_TOKEN_HEADER
                        )
        );
    }

    @Test
    void shouldPassThroughRequestsWithoutInternalToken() {

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/rooms")
                .build();

        filter.filter(MockServerWebExchange.from(request), chain).block();

        assertFalse(
                forwardedExchange().getRequest()
                        .getHeaders()
                        .containsKey(
                                InternalTokenStrippingFilter.INTERNAL_TOKEN_HEADER
                        )
        );
    }

    private ServerWebExchange forwardedExchange() {

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        verify(chain).filter(captor.capture());

        return captor.getValue();
    }
}
