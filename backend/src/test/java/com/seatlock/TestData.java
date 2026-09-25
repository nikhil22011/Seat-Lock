package com.seatlock;

import com.seatlock.event.EventDtos.CreateEventRequest;
import com.seatlock.event.EventDtos.RowSpec;
import com.seatlock.event.EventService;
import com.seatlock.event.SeatRepository;
import com.seatlock.user.Role;
import com.seatlock.user.User;
import com.seatlock.user.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Small factory so every test builds its own isolated users and events. */
@Component
public class TestData {

    private final UserRepository users;
    private final EventService events;
    private final SeatRepository seats;

    public TestData(UserRepository users, EventService events, SeatRepository seats) {
        this.users = users;
        this.events = events;
        this.seats = seats;
    }

    public User user(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        return users.save(new User("Test " + role, email, "{noop}unused", role));
    }

    /** An event with a single row "A" of {@code seatCount} seats priced at 100.00 each. */
    public long event(User organizer, int seatCount) {
        return events.create(organizer.getId(), new CreateEventRequest(
                "Test Event " + UUID.randomUUID(), "desc", "Test Hall", "Pune",
                Instant.now().plus(Duration.ofDays(3)), null,
                List.of(new RowSpec("A", seatCount, "Regular", 10000)))).id();
    }

    public List<Long> seatIds(long eventId) {
        return seats.findByEventIdOrderByRowLabelAscNumberAsc(eventId).stream().map(s -> s.getId()).toList();
    }
}
