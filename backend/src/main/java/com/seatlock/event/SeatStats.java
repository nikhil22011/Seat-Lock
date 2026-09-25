package com.seatlock.event;

/** Per-event seat aggregates, computed in a single GROUP BY query. */
public record SeatStats(
        Long eventId,
        Integer minPriceCents,
        Integer maxPriceCents,
        Long totalSeats,
        Long availableSeats,
        Long bookedSeats,
        Long revenueCents
) {
    public static SeatStats empty(Long eventId) {
        return new SeatStats(eventId, 0, 0, 0L, 0L, 0L, 0L);
    }
}
