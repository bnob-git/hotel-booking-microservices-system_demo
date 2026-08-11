package com.hotel.apigateway.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;


import static org.assertj.core.api.Assertions.assertThat;

class GlobalGatewayExceptionHandlerTest {

    private GlobalGatewayExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalGatewayExceptionHandler();
    }

    @Test
    void shouldReturnStatusFromResponseStatusException() {

        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/unknown").build()
                );

        handler.handle(
                exchange,
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Route not found"
                )
        ).block();

        MockServerHttpResponse response =
                exchange.getResponse();

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        String body = getResponseBody(response);

        assertThat(body)
                .contains("\"status\": 404")
                .contains("\"error\": \"Not Found\"")
                .contains("\"code\": \"ROUTE_NOT_FOUND\"")
                .contains("\"message\": \"Route not found\"")
                .contains("\"path\": \"/api/unknown\"");
    }

    @Test
    void shouldReturnForbiddenWhenAccessIsDenied() {

        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/chat").build()
                );

        handler.handle(
                exchange,
                new AccessDeniedException("Access denied")
        ).block();

        MockServerHttpResponse response =
                exchange.getResponse();

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String body = getResponseBody(response);

        assertThat(body)
                .contains("\"status\": 403")
                .contains("\"error\": \"Forbidden\"")
                .contains("\"code\": \"ACCESS_DENIED\"")
                .contains("\"message\": \"Access denied\"")
                .contains("\"path\": \"/api/chat\"");
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedException() {

        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/chat").build()
                );

        handler.handle(
                exchange,
                new RuntimeException("Unexpected failure")
        ).block();

        MockServerHttpResponse response =
                exchange.getResponse();

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        String body = getResponseBody(response);

        assertThat(body)
                .contains("\"status\": 500")
                .contains("\"error\": \"Internal Server Error\"")
                .contains("\"code\": \"UNEXPECTED_GATEWAY_ERROR\"")
                .contains("\"message\": \"Unexpected gateway error\"")
                .contains("\"path\": \"/api/chat\"");
    }

    private String getResponseBody(MockServerHttpResponse response) {

        return response.getBodyAsString()
                .blockOptional()
                .orElse("");
    }
}
