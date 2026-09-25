package com.seatlock.booking;

import com.seatlock.event.Event;
import com.seatlock.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_user", columnList = "user_id"),
        @Index(name = "idx_bookings_status_expiry", columnList = "status, expiresAt")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.HELD;

    @Column(nullable = false)
    private int seatCount;

    @Column(nullable = false)
    private int amountCents;

    /** When the seat hold runs out if the user hasn't paid. */
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant confirmedAt;

    private Instant checkedInAt;

    @Column(length = 100)
    private String paymentRef;

    protected Booking() {}

    public Booking(User user, Event event, int seatCount, int amountCents, Instant expiresAt) {
        this.user = user;
        this.event = event;
        this.seatCount = seatCount;
        this.amountCents = amountCents;
        this.expiresAt = expiresAt;
    }

    public boolean isHoldActive(Instant now) {
        return status == BookingStatus.HELD && expiresAt.isAfter(now);
    }

    public void confirm(String paymentRef, Instant now) {
        this.status = BookingStatus.CONFIRMED;
        this.paymentRef = paymentRef;
        this.confirmedAt = now;
    }

    public void expire() {
        this.status = BookingStatus.EXPIRED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Event getEvent() { return event; }
    public BookingStatus getStatus() { return status; }
    public int getSeatCount() { return seatCount; }
    public int getAmountCents() { return amountCents; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public String getPaymentRef() { return paymentRef; }
}
