package com.seatlock.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sweeps unpaid holds whose timer ran out and gives the seats back, so everyone watching the
 * seat map sees them turn green again. Each booking is expired in its own small transaction.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingRepository bookings;
    private final BookingService service;

    public HoldExpiryJob(BookingRepository bookings, BookingService service) {
        this.bookings = bookings;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${seatlock.expiry-sweep-interval:PT10S}")
    public void sweep() {
        int expired = 0;
        for (Long id : bookings.findExpiredHoldIds(Instant.now())) {
            try {
                if (service.expireIfOverdue(id)) expired++;
            } catch (RuntimeException e) {
                log.warn("Could not expire booking {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) log.info("Released seats from {} expired hold(s)", expired);
    }
}
