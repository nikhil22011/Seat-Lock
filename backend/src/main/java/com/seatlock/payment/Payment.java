package com.seatlock.payment;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One payment attempt. The unique idempotency key is what stops a double-click (or a network
 * retry) from charging the user twice: the second request finds this row and replays its result.
 */
@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotencyKey"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 100)
    private String providerRef;

    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Payment() {}

    public Payment(Long bookingId, Long userId, String idempotencyKey, int amountCents, PaymentResult result) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.amountCents = amountCents;
        this.status = result.status();
        this.providerRef = result.providerRef();
        this.failureReason = result.failureReason();
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getAmountCents() { return amountCents; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderRef() { return providerRef; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
