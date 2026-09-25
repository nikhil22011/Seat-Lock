package com.seatlock.config;

import com.seatlock.event.EventDtos.CreateEventRequest;
import com.seatlock.event.EventDtos.RowSpec;
import com.seatlock.event.EventService;
import com.seatlock.user.Role;
import com.seatlock.user.User;
import com.seatlock.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty dev database with demo accounts and events so the app is usable immediately.
 * Demo logins (password "password123"): user@seatlock.dev, organizer@seatlock.dev
 */
@Component
@Profile("dev")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "password123";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EventService events;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder, EventService events) {
        this.users = users;
        this.encoder = encoder;
        this.events = events;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;

        String hash = encoder.encode(DEMO_PASSWORD);
        User organizer = users.save(new User("Priya Sharma", "organizer@seatlock.dev", hash, Role.ORGANIZER));
        users.save(new User("Rahul Verma", "user@seatlock.dev", hash, Role.USER));
        users.save(new User("Ananya Iyer", "user2@seatlock.dev", hash, Role.USER));

        Instant base = Instant.now().truncatedTo(ChronoUnit.HOURS);

        events.create(organizer.getId(), new CreateEventRequest(
                "Arijit Singh Live in Concert",
                "An unforgettable evening of soulful melodies with a 40-piece live orchestra.",
                "Jawaharlal Nehru Stadium", "Delhi", base.plus(Duration.ofDays(12)).plus(Duration.ofHours(6)), null,
                theatre(new String[]{"A", "B"}, 14, "VIP", 499900,
                        new String[]{"C", "D", "E", "F"}, 16, "Gold", 249900,
                        new String[]{"G", "H", "I", "J"}, 18, "Silver", 99900)));

        events.create(organizer.getId(), new CreateEventRequest(
                "Zakir Khan: Papa Yaar",
                "Stand-up comedy special. Recommended for ages 16+.",
                "Phoenix Marketcity Auditorium", "Bengaluru", base.plus(Duration.ofDays(5)).plus(Duration.ofHours(3)), null,
                theatre(new String[]{"A", "B", "C"}, 12, "Premium", 149900,
                        new String[]{"D", "E", "F", "G"}, 14, "Regular", 79900,
                        new String[]{}, 0, "", 0)));

        events.create(organizer.getId(), new CreateEventRequest(
                "Dune: Part Three - IMAX Premiere",
                "First-day-first-show fan premiere in IMAX with laser projection.",
                "PVR IMAX Lower Parel", "Mumbai", base.plus(Duration.ofDays(2)).plus(Duration.ofHours(8)), null,
                theatre(new String[]{"A", "B", "C", "D", "E"}, 20, "Classic", 45000,
                        new String[]{"F", "G", "H"}, 20, "Prime", 65000,
                        new String[]{"J"}, 12, "Recliner", 120000)));

        events.create(organizer.getId(), new CreateEventRequest(
                "Sunburn Arena ft. Martin Garrix",
                "Asia's biggest electronic music festival brand, arena edition.",
                "Mahalaxmi Race Course", "Mumbai", base.plus(Duration.ofDays(21)).plus(Duration.ofHours(10)), null,
                theatre(new String[]{"A", "B"}, 20, "Fan Pit", 699900,
                        new String[]{"C", "D", "E", "F", "G", "H"}, 24, "General", 299900,
                        new String[]{}, 0, "", 0)));

        log.info("Seeded demo data. Log in as user@seatlock.dev or organizer@seatlock.dev with password '{}'", DEMO_PASSWORD);
    }

    /** Builds rows for up to three price tiers, front to back. */
    private static List<RowSpec> theatre(String[] rows1, int seats1, String cat1, int price1,
                                         String[] rows2, int seats2, String cat2, int price2,
                                         String[] rows3, int seats3, String cat3, int price3) {
        List<RowSpec> rows = new ArrayList<>();
        for (String r : rows1) rows.add(new RowSpec(r, seats1, cat1, price1));
        for (String r : rows2) rows.add(new RowSpec(r, seats2, cat2, price2));
        for (String r : rows3) rows.add(new RowSpec(r, seats3, cat3, price3));
        return rows;
    }
}
