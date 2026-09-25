package com.seatlock.realtime;

import com.seatlock.event.SeatStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class SeatBroadcaster {

    /** Tiny delta message: clients patch just these seats in their local seat map. */
    public record SeatUpdate(Long id, SeatStatus status) {}

    private final SimpMessagingTemplate messaging;

    public SeatBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /**
     * AFTER_COMMIT matters: if we broadcast inside the transaction and it then rolls back,
     * other users would see a seat turn grey that was never actually taken.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatsChanged(SeatsChangedEvent change) {
        if (change.seatIds().isEmpty()) return;
        List<SeatUpdate> updates = change.seatIds().stream()
                .map(id -> new SeatUpdate(id, change.newStatus()))
                .toList();
        messaging.convertAndSend("/topic/events/" + change.eventId() + "/seats", updates);
    }
}
