package com.seatlock.event;

public enum SeatStatus {
    AVAILABLE,
    /** Temporarily reserved while the user pays. Expires after seatlock.hold-duration. */
    HELD,
    BOOKED
}
