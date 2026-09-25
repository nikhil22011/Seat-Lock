package com.seatlock.event;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One physical seat for one event. Its status only ever changes through the conditional
 * UPDATE statements in {@link SeatRepository}, which is what makes double booking impossible.
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_seat_position", columnNames = {"event_id", "rowLabel", "number"}),
        indexes = {
                @Index(name = "idx_seats_event", columnList = "event_id"),
                @Index(name = "idx_seats_booking", columnList = "bookingId")
        })
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false, length = 4)
    private String rowLabel;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false, length = 30)
    private String category;

    /** Price in the smallest currency unit (paise) to avoid floating-point money bugs. */
    @Column(nullable = false)
    private int priceCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /** The booking currently holding or owning this seat, if any. */
    private Long bookingId;

    private Instant holdExpiresAt;

    @Version
    private long version;

    protected Seat() {}

    public Seat(Event event, String rowLabel, int number, String category, int priceCents) {
        this.event = event;
        this.rowLabel = rowLabel;
        this.number = number;
        this.category = category;
        this.priceCents = priceCents;
    }

    /** A hold whose timer ran out counts as free, even before the cleanup job releases it. */
    public SeatStatus effectiveStatus(Instant now) {
        if (status == SeatStatus.HELD && holdExpiresAt != null && holdExpiresAt.isBefore(now)) {
            return SeatStatus.AVAILABLE;
        }
        return status;
    }

    public String label() {
        return rowLabel + number;
    }

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public String getRowLabel() { return rowLabel; }
    public int getNumber() { return number; }
    public String getCategory() { return category; }
    public int getPriceCents() { return priceCents; }
    public SeatStatus getStatus() { return status; }
    public Long getBookingId() { return bookingId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}
