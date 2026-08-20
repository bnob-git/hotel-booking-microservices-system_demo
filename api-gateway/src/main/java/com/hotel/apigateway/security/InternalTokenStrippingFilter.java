package com.hotel.apigateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Removes any client-supplied internal service token so an external caller
 * cannot mint one and reach the internal endpoints of the downstream services.
 */
@Component
public class InternalTokenStrippingFilter implements GlobalFilter, Ordered {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        if (!exchange.getRequest().getHeaders().containsKey(INTERNAL_TOKEN_HEADER)) {
            return chain.filter(exchange);
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request -> request.headers(
                        headers -> headers.remove(INTERNAL_TOKEN_HEADER)
                ))
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
