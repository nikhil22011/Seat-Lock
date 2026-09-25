package com.seatlock.booking;

import com.seatlock.booking.BookingDtos.*;
import com.seatlock.common.ApiException;
import com.seatlock.config.SeatLockProperties;
import com.seatlock.event.Event;
import com.seatlock.event.EventRepository;
import com.seatlock.event.Seat;
import com.seatlock.event.SeatRepository;
import com.seatlock.event.SeatStatus;
import com.seatlock.payment.Payment;
import com.seatlock.payment.PaymentGateway;
import com.seatlock.payment.PaymentRepository;
import com.seatlock.payment.PaymentResult;
import com.seatlock.payment.PaymentStatus;
import com.seatlock.realtime.SeatsChangedEvent;
import com.seatlock.ticket.TicketCodec;
import com.seatlock.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final EventRepository events;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final TicketCodec tickets;
    private final ApplicationEventPublisher publisher;
    private final SeatLockProperties props;

    public BookingService(BookingRepository bookings, SeatRepository seats, EventRepository events,
                          UserRepository users, PaymentRepository payments, PaymentGateway gateway,
                          TicketCodec tickets, ApplicationEventPublisher publisher, SeatLockProperties props) {
        this.bookings = bookings;
        this.seats = seats;
        this.events = events;
        this.users = users;
        this.payments = payments;
        this.gateway = gateway;
        this.tickets = tickets;
        this.publisher = publisher;
        this.props = props;
    }

    // ---------------------------------------------------------------- hold

    /**
     * Step 1 of checkout: reserve seats for a few minutes. All-or-nothing: if even one seat
     * was taken by someone else, nothing is held and the user gets 409 SEAT_UNAVAILABLE.
     */
    @Transactional
    public BookingView hold(Long userId, Long eventId, List<Long> requestedSeatIds) {
        Set<Long> seatIds = new LinkedHashSet<>(requestedSeatIds);
        if (seatIds.size() > props.maxSeatsPerBooking()) {
            throw ApiException.badRequest("TOO_MANY_SEATS",
                    "You can book at most " + props.maxSeatsPerBooking() + " seats at a time");
        }
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        Instant now = Instant.now();
        if (!event.getStartsAt().isAfter(now)) {
            throw ApiException.badRequest("EVENT_STARTED", "This event has already started");
        }

        List<Seat> chosen = seats.findByEventIdAndIdIn(eventId, seatIds);
        if (chosen.size() != seatIds.size()) {
            throw ApiException.badRequest("INVALID_SEATS", "Some seats don't belong to this event");
        }

        // One active hold per user per event: picking new seats replaces the old selection.
        for (Booking previous : bookings.findByUserIdAndEventIdAndStatus(userId, eventId, BookingStatus.HELD)) {
            releaseHold(previous, BookingStatus.CANCELLED);
        }

        int amount = chosen.stream().mapToInt(Seat::getPriceCents).sum();
        Instant expiresAt = now.plus(props.holdDuration());
        Booking booking = bookings.save(new Booking(users.getReferenceById(userId), event, chosen.size(), amount, expiresAt));

        int held;
        try {
            held = seats.holdSeats(eventId, seatIds, booking.getId(), now, expiresAt);
        } catch (ConcurrencyFailureException e) {
            // Some databases abort the loser of a row-lock race instead of returning 0 rows.
            held = -1;
        }
        if (held != seatIds.size()) {
            // Throwing rolls back the whole transaction, including any seats we did manage to grab.
            throw ApiException.conflict("SEAT_UNAVAILABLE",
                    "Sorry, one or more of those seats was just taken. Please pick again.");
        }

        publisher.publishEvent(new SeatsChangedEvent(eventId, seatIds, SeatStatus.HELD));
        return toView(booking, event, labels(chosen));
    }

    // ---------------------------------------------------------------- pay

    /**
     * Step 2 of checkout. Safe to call any number of times with the same Idempotency-Key:
     * only the first call charges; repeats get the stored result back.
     */
    @Transactional
    public PayResponse pay(Long userId, Long bookingId, String idempotencyKey, String simulate) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "Send a unique Idempotency-Key header (max 64 chars) with each payment attempt");
        }

        // Lock first, then look for a previous attempt: two identical requests arriving together
        // are serialized here, so the second one is guaranteed to see the first one's payment row.
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow(() -> ApiException.notFound("Booking"));
        requireOwner(booking, userId);

        Optional<Payment> previous = payments.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            Payment p = previous.get();
            if (!p.getBookingId().equals(bookingId)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was used for another booking");
            }
            return new PayResponse(p.getStatus(), replayMessage(p), view(booking));
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw ApiException.conflict("ALREADY_PAID", "This booking is already paid");
        }
        Instant now = Instant.now();
        if (!booking.isHoldActive(now)) {
            throw new ApiException(HttpStatus.GONE, "HOLD_EXPIRED", "Your seat hold has expired. Please select seats again.");
        }

        PaymentResult result = gateway.charge(bookingId, booking.getAmountCents(), idempotencyKey, simulate);
        payments.save(new Payment(bookingId, userId, idempotencyKey, booking.getAmountCents(), result));

        if (!result.succeeded()) {
            // Seats stay held so the user can retry with another card before the timer runs out.
            return new PayResponse(PaymentStatus.FAILED, result.failureReason(), view(booking));
        }

        int confirmed = seats.confirmSeats(bookingId);
        if (confirmed != booking.getSeatCount()) {
            // Only reachable if the hold expired in the instant between our check and this update.
            // With a real gateway this is where we'd issue a refund.
            throw new ApiException(HttpStatus.GONE, "HOLD_EXPIRED", "Your seat hold expired during payment.");
        }
        booking.confirm(result.providerRef(), now);
        publisher.publishEvent(new SeatsChangedEvent(booking.getEvent().getId(),
                seats.findIdsByBookingId(bookingId), SeatStatus.BOOKED));
        return new PayResponse(PaymentStatus.SUCCEEDED, "Payment successful. Enjoy the show!", view(booking));
    }

    // ---------------------------------------------------------------- cancel / expire

    @Transactional
    public BookingView cancel(Long userId, Long bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow(() -> ApiException.notFound("Booking"));
        requireOwner(booking, userId);
        if (booking.getStatus() != BookingStatus.HELD) {
            throw ApiException.conflict("NOT_CANCELLABLE", "Only an unpaid hold can be cancelled");
        }
        List<String> seatLabels = labels(seats.findByBookingIdInOrderByRowLabelAscNumberAsc(List.of(bookingId)));
        releaseHold(booking, BookingStatus.CANCELLED);
        return toView(booking, booking.getEvent(), seatLabels);
    }

    /** Called by {@link HoldExpiryJob} for each overdue hold, one transaction per booking. */
    @Transactional
    public boolean expireIfOverdue(Long bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.HELD || booking.isHoldActive(Instant.now())) {
            return false; // paid or cancelled while we were waiting for the lock
        }
        releaseHold(booking, BookingStatus.EXPIRED);
        return true;
    }

    private void releaseHold(Booking booking, BookingStatus endState) {
        List<Long> seatIds = seats.findIdsByBookingId(booking.getId());
        seats.releaseSeats(booking.getId());
        if (endState == BookingStatus.EXPIRED) booking.expire();
        else booking.cancel();
        publisher.publishEvent(new SeatsChangedEvent(booking.getEvent().getId(), seatIds, SeatStatus.AVAILABLE));
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<BookingView> forUser(Long userId) {
        List<Booking> list = bookings.findForUser(userId);
        if (list.isEmpty()) return List.of();
        Map<Long, List<String>> seatLabels = seats.findByBookingIdInOrderByRowLabelAscNumberAsc(
                        list.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.groupingBy(Seat::getBookingId, LinkedHashMap::new,
                        Collectors.mapping(Seat::label, Collectors.toList())));
        return list.stream()
                .map(b -> toView(b, b.getEvent(), seatLabels.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingView get(Long userId, Long bookingId) {
        Booking booking = bookings.findWithDetails(bookingId).orElseThrow(() -> ApiException.notFound("Booking"));
        requireOwner(booking, userId);
        return view(booking);
    }

    // ---------------------------------------------------------------- check-in

    @Transactional
    public CheckInResult checkIn(Long organizerId, String code) {
        Long bookingId = tickets.decode(code)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TICKET", "This QR code is not a genuine SeatLock ticket"));
        Booking booking = bookings.findWithDetails(bookingId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TICKET", "Ticket not found"));
        if (!booking.getEvent().getOrganizer().getId().equals(organizerId)) {
            throw ApiException.forbidden("This ticket is for an event you don't organise");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("TICKET_NOT_VALID", "This booking was never paid");
        }
        List<String> seatLabels = labels(seats.findByBookingIdInOrderByRowLabelAscNumberAsc(List.of(bookingId)));
        Instant now = Instant.now();
        if (bookings.markCheckedIn(bookingId, now) == 0) {
            // Another scanner may have won the race after we loaded the booking, so fall back to "now".
            Instant usedAt = booking.getCheckedInAt() != null ? booking.getCheckedInAt() : now;
            throw ApiException.conflict("ALREADY_CHECKED_IN", "Already used! This ticket was scanned before.")
                    .with("checkedInAt", usedAt.toString())
                    .with("attendeeName", booking.getUser().getName())
                    .with("seats", seatLabels);
        }
        return new CheckInResult(bookingId, booking.getUser().getName(), booking.getEvent().getTitle(), seatLabels, now);
    }

    // ---------------------------------------------------------------- helpers

    private BookingView view(Booking booking) {
        List<String> seatLabels = labels(seats.findByBookingIdInOrderByRowLabelAscNumberAsc(List.of(booking.getId())));
        return toView(booking, booking.getEvent(), seatLabels);
    }

    private BookingView toView(Booking b, Event e, List<String> seatLabels) {
        String ticket = b.getStatus() == BookingStatus.CONFIRMED ? tickets.encode(b.getId()) : null;
        return new BookingView(b.getId(), b.getStatus(),
                new EventRef(e.getId(), e.getTitle(), e.getVenue(), e.getCity(), e.getStartsAt()),
                seatLabels, b.getAmountCents(), b.getExpiresAt(), b.getCreatedAt(), b.getConfirmedAt(),
                b.getCheckedInAt(), ticket);
    }

    private static List<String> labels(List<Seat> list) {
        return list.stream()
                .sorted(Comparator.comparing(Seat::getRowLabel).thenComparingInt(Seat::getNumber))
                .map(Seat::label)
                .toList();
    }

    private static void requireOwner(Booking booking, Long userId) {
        if (!booking.getUser().getId().equals(userId)) {
            throw ApiException.notFound("Booking"); // don't reveal other people's booking ids
        }
    }

    private static String replayMessage(Payment p) {
        return p.getStatus() == PaymentStatus.SUCCEEDED
                ? "Payment already processed (duplicate request ignored)"
                : p.getFailureReason();
    }
}
