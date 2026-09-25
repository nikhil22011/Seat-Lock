package com.seatlock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * All app-specific settings live under the "seatlock" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "seatlock")
public record SeatLockProperties(
        Jwt jwt,
        Duration holdDuration,
        int maxSeatsPerBooking,
        String ticketSecret,
        List<String> allowedOrigins
) {
    public record Jwt(String secret, Duration ttl, String issuer) {}
}
