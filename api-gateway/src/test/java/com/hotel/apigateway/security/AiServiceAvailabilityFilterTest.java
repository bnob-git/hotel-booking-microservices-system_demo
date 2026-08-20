package com.hotel.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AiServiceAvailabilityFilterTest {

    @Test
    void shouldReturn503WhenAiIsDisabled() {

        // Arrange
        AiServiceAvailabilityFilter filter =
                new AiServiceAvailabilityFilter(false);

        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/chat")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exchange.getResponse().getStatusCode()
        );

        verifyNoInteractions(chain);
    }

    @Test
    void shouldContinueChainWhenAiIsEnabled() {

        // Arrange
        AiServiceAvailabilityFilter filter =
                new AiServiceAvailabilityFilter(true);

        MockServerHttpRequest request =
                MockServerHttpRequest.post("/api/chat")
                        .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(chain.filter(exchange))
                .thenReturn(Mono.empty());

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(exchange);
    }
}
