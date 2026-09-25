package com.seatlock.booking;

/**
 * HELD --pay--> CONFIRMED
 *   |--timer--> EXPIRED
 *   `--user---> CANCELLED
 */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    EXPIRED,
    CANCELLED
}
