package com.hotel.transaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mainframe")
public record MainframeProperties(
        String mode,
        Mq mq,
        long requestTimeoutMs
) {

    public record Mq(String host, int port, String queueManager, String requestQueue, String responseQueue) {
    }
}
