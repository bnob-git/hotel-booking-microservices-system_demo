package com.hotel.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mainframe")
public record MainframeProperties(
        String mode,
        Cics cics,
        long requestTimeoutMs
) {

    public record Cics(String host, int port) {
    }
}
