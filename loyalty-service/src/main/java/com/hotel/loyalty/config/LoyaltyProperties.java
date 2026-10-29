package com.hotel.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "loyalty.points")
public record LoyaltyProperties(
        int perEuro,
        BigDecimal value,
        int redemptionIncrement
) {

    public LoyaltyProperties {
        if (perEuro <= 0) {
            perEuro = 10;
        }
        if (value == null || value.signum() <= 0) {
            value = new BigDecimal("0.05");
        }
        if (redemptionIncrement <= 0) {
            redemptionIncrement = 100;
        }
    }

    public static LoyaltyProperties defaults() {
        return new LoyaltyProperties(10, new BigDecimal("0.05"), 100);
    }
}
