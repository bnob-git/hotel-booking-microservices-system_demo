package com.hotel.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically expires unpaid holds ({@code booking.hold.expiry-interval}, default 60s). */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingLifecycleService lifecycleService;

    public HoldExpiryJob(BookingLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(
            fixedDelayString = "${booking.hold.expiry-interval:PT60S}",
            initialDelayString = "${booking.hold.expiry-interval:PT60S}"
    )
    public int expireHolds() {
        int expired = lifecycleService.expireLapsedHolds();
        if (expired > 0) {
            log.info("Expired {} unpaid booking hold(s)", expired);
        }
        return expired;
    }
}
