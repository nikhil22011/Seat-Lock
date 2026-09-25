package com.seatlock.booking;

import com.seatlock.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {}

    public record HoldRequest(@NotEmpty @Size(max = 20) List<Long> seatIds) {}

    /** "simulate" lets the demo show a failed card; a real gateway would ignore it. */
    public record PayRequest(String simulate) {}

    public record EventRef(Long id, String title, String venue, String city, Instant startsAt) {}

    public record BookingView(
            Long id, BookingStatus status, EventRef event, List<String> seats, int amountCents,
            Instant expiresAt, Instant createdAt, Instant confirmedAt, Instant checkedInAt,
            /** Only present once the booking is CONFIRMED. This is what the QR code encodes. */
            String ticketCode
    ) {}

    public record PayResponse(PaymentStatus paymentStatus, String message, BookingView booking) {}

    public record CheckInRequest(@NotBlank @Size(max = 200) String code) {}

    public record CheckInResult(
            Long bookingId, String attendeeName, String eventTitle, List<String> seats, Instant checkedInAt
    ) {}
}
