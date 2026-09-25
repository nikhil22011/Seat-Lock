package com.seatlock;

import com.seatlock.booking.BookingService;
import com.seatlock.common.ApiException;
import com.seatlock.event.Seat;
import com.seatlock.event.SeatRepository;
import com.seatlock.event.SeatStatus;
import com.seatlock.user.Role;
import com.seatlock.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the headline claim: no matter how many people click "Book" on the same seat at the
 * same instant, exactly one of them gets it.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeatHoldConcurrencyTest {

    @Autowired BookingService bookings;
    @Autowired SeatRepository seats;
    @Autowired TestData data;

    @Test
    void fiftyUsersRaceForOneSeat_exactlyOneWins() throws Exception {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 5);
        Long seatId = data.seatIds(eventId).getFirst();

        int racers = 50;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < racers; i++) users.add(data.user(Role.USER));

        AtomicInteger wins = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        runAllAtOnce(racers, i -> {
            try {
                bookings.hold(users.get(i).getId(), eventId, List.of(seatId));
                wins.incrementAndGet();
            } catch (ApiException e) {
                if ("SEAT_UNAVAILABLE".equals(e.getCode())) rejected.incrementAndGet();
            }
        });

        assertThat(wins.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(racers - 1);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void overlappingMultiSeatRequests_areAllOrNothing() throws Exception {
        User organizer = data.user(Role.ORGANIZER);
        long eventId = data.event(organizer, 3);
        List<Long> ids = data.seatIds(eventId); // A1, A2, A3
        User alice = data.user(Role.USER);
        User bob = data.user(Role.USER);

        // Both want A2. Alice also wants A1, Bob also wants A3.
        AtomicInteger wins = new AtomicInteger();
        runAllAtOnce(2, i -> {
            try {
                if (i == 0) bookings.hold(alice.getId(), eventId, List.of(ids.get(0), ids.get(1)));
                else bookings.hold(bob.getId(), eventId, List.of(ids.get(1), ids.get(2)));
                wins.incrementAndGet();
            } catch (ApiException ignored) {
            }
        });

        assertThat(wins.get()).isEqualTo(1);
        Map<Long, Seat> byId = seats.findAllById(ids).stream().collect(Collectors.toMap(Seat::getId, Function.identity()));
        long held = byId.values().stream().filter(s -> s.getStatus() == SeatStatus.HELD).count();
        // The loser must not keep a "partial" seat: exactly 2 held, 1 still free.
        assertThat(held).isEqualTo(2);
        assertThat(byId.get(ids.get(1)).getStatus()).isEqualTo(SeatStatus.HELD);
    }

    /** Starts every task at the same moment using a latch, then waits for all of them. */
    private static void runAllAtOnce(int n, java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                task.accept(idx);
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
