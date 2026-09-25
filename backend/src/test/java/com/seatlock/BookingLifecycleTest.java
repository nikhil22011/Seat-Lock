package com.seatlock;

import com.seatlock.booking.BookingDtos.BookingView;
import com.seatlock.booking.BookingDtos.PayResponse;
import com.seatlock.booking.BookingService;
import com.seatlock.booking.BookingStatus;
import com.seatlock.booking.HoldExpiryJob;
import com.seatlock.common.ApiException;
import com.seatlock.event.SeatRepository;
import com.seatlock.event.SeatStatus;
import com.seatlock.payment.PaymentRepository;
import com.seatlock.payment.PaymentStatus;
import com.seatlock.user.Role;
import com.seatlock.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BookingLifecycleTest {

    @Autowired BookingService bookings;
    @Autowired SeatRepository seats;
    @Autowired PaymentRepository payments;
    @Autowired HoldExpiryJob expiryJob;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    @Test
    void holdPayAndCheckIn_happyPath() {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 4);
        List<Long> ids = data.seatIds(eventId);
        User user = data.user(Role.USER);

        BookingView held = bookings.hold(user.getId(), eventId, List.of(ids.get(0), ids.get(1)));
        assertThat(held.status()).isEqualTo(BookingStatus.HELD);
        assertThat(held.amountCents()).isEqualTo(20000);
        assertThat(held.seats()).containsExactly("A1", "A2");

        PayResponse paid = bookings.pay(user.getId(), held.id(), key(), null);
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(paid.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paid.booking().ticketCode()).startsWith("SL1.");
        assertThat(seats.findById(ids.get(0)).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);

        var result = bookings.checkIn(organizer.getId(), paid.booking().ticketCode());
        assertThat(result.seats()).containsExactly("A1", "A2");

        // Same ticket scanned again at the gate: rejected.
        assertThatThrownBy(() -> bookings.checkIn(organizer.getId(), paid.booking().ticketCode()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ALREADY_CHECKED_IN");
    }

    @Test
    void forgedTicket_isRejected() {
        User organizer = data.user(Role.ORGANIZER);
        assertThatThrownBy(() -> bookings.checkIn(organizer.getId(), "SL1.1.AAAAAAAAAAAAAAAAAAAAAA"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_TICKET");
    }

    @Test
    void doubleClickOnPay_chargesOnce() throws Exception {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 2);
        User user = data.user(Role.USER);
        BookingView held = bookings.hold(user.getId(), eventId, List.of(data.seatIds(eventId).getFirst()));

        // 10 identical requests (same Idempotency-Key) fired at once, like a frantic double click.
        String sameKey = key();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<PayResponse>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> bookings.pay(user.getId(), held.id(), sameKey, null)));
        }
        for (Future<PayResponse> f : results) {
            assertThat(f.get(30, TimeUnit.SECONDS).booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        }
        pool.shutdown();

        assertThat(payments.countByBookingIdAndStatus(held.id(), PaymentStatus.SUCCEEDED)).isEqualTo(1);

        // A *new* key for an already-paid booking is refused rather than charged.
        assertThatThrownBy(() -> bookings.pay(user.getId(), held.id(), key(), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ALREADY_PAID");
    }

    @Test
    void declinedCard_keepsSeatsHeld_andRetryCanSucceed() {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 2);
        User user = data.user(Role.USER);
        Long seatId = data.seatIds(eventId).getFirst();
        BookingView held = bookings.hold(user.getId(), eventId, List.of(seatId));

        PayResponse declined = bookings.pay(user.getId(), held.id(), key(), "FAIL");
        assertThat(declined.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.booking().status()).isEqualTo(BookingStatus.HELD);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);

        PayResponse retry = bookings.pay(user.getId(), held.id(), key(), null);
        assertThat(retry.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void expiredHold_releasesSeats_andBlocksPayment() {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 2);
        User user = data.user(Role.USER);
        Long seatId = data.seatIds(eventId).getFirst();
        BookingView held = bookings.hold(user.getId(), eventId, List.of(seatId));

        // Fast-forward: pretend the 5-minute timer ran out.
        jdbc.update("update bookings set expires_at = dateadd('MINUTE', -1, current_timestamp) where id = ?", held.id());
        jdbc.update("update seats set hold_expires_at = dateadd('MINUTE', -1, current_timestamp) where booking_id = ?", held.id());

        assertThatThrownBy(() -> bookings.pay(user.getId(), held.id(), key(), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("HOLD_EXPIRED");

        expiryJob.sweep();
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(bookings.get(user.getId(), held.id()).status()).isEqualTo(BookingStatus.EXPIRED);

        // Someone else can now grab it.
        User other = data.user(Role.USER);
        assertThat(bookings.hold(other.getId(), eventId, List.of(seatId)).status()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void usersCannotSeeOrPayOthersBookings() {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 2);
        User owner = data.user(Role.USER);
        User stranger = data.user(Role.USER);
        BookingView held = bookings.hold(owner.getId(), eventId, List.of(data.seatIds(eventId).getFirst()));

        assertThatThrownBy(() -> bookings.pay(stranger.getId(), held.id(), key(), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
