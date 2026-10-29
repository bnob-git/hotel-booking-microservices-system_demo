package com.hotel.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (hold expiry). Disabled in tests via {@code booking.scheduling.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "booking.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
