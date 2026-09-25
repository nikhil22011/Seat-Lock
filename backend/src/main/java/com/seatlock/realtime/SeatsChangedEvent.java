package com.seatlock.realtime;

import com.seatlock.event.SeatStatus;

import java.util.Collection;

/** Published inside a transaction whenever seats change state; broadcast only after commit. */
public record SeatsChangedEvent(Long eventId, Collection<Long> seatIds, SeatStatus newStatus) {}
