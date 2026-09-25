package com.seatlock.event;

import com.seatlock.booking.BookingRepository;
import com.seatlock.common.ApiException;
import com.seatlock.event.EventDtos.*;
import com.seatlock.user.User;
import com.seatlock.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository events;
    private final SeatRepository seats;
    private final UserRepository users;
    private final BookingRepository bookings;

    public EventService(EventRepository events, SeatRepository seats, UserRepository users, BookingRepository bookings) {
        this.events = events;
        this.seats = seats;
        this.users = users;
        this.bookings = bookings;
    }

    public List<EventSummary> upcoming() {
        List<Event> list = events.findByStartsAtAfterOrderByStartsAtAsc(Instant.now());
        Map<Long, SeatStats> stats = statsById(list);
        return list.stream()
                .map(e -> EventSummary.of(e, stats.getOrDefault(e.getId(), SeatStats.empty(e.getId()))))
                .toList();
    }

    public EventDetail detail(Long id) {
        Event e = events.findWithOrganizer(id).orElseThrow(() -> ApiException.notFound("Event"));
        Instant now = Instant.now();
        List<Seat> all = seats.findByEventIdOrderByRowLabelAscNumberAsc(id);

        // One entry per category, cheapest first.
        Map<String, Integer> categories = new LinkedHashMap<>();
        all.stream()
                .sorted(Comparator.comparingInt(Seat::getPriceCents))
                .forEach(s -> categories.putIfAbsent(s.getCategory(), s.getPriceCents()));

        long available = all.stream().filter(s -> s.effectiveStatus(now) == SeatStatus.AVAILABLE).count();
        return new EventDetail(e.getId(), e.getTitle(), e.getDescription(), e.getVenue(), e.getCity(),
                e.getStartsAt(), e.getImageUrl(), e.getOrganizer().getName(),
                categories.entrySet().stream().map(c -> new PriceCategory(c.getKey(), c.getValue())).toList(),
                all.size(), available);
    }

    public List<SeatView> seatMap(Long eventId) {
        if (!events.existsById(eventId)) throw ApiException.notFound("Event");
        Instant now = Instant.now();
        return seats.findByEventIdOrderByRowLabelAscNumberAsc(eventId).stream()
                .map(s -> SeatView.of(s, now))
                .toList();
    }

    @Transactional
    public EventDetail create(Long organizerId, CreateEventRequest req) {
        Set<String> labels = new HashSet<>();
        for (RowSpec row : req.rows()) {
            if (!labels.add(row.label())) {
                throw ApiException.badRequest("DUPLICATE_ROW", "Row " + row.label() + " is listed twice");
            }
        }
        User organizer = users.getReferenceById(organizerId);
        Event event = events.save(new Event(req.title().trim(), req.description(), req.venue().trim(),
                req.city().trim(), req.startsAt(), blankToNull(req.imageUrl()), organizer));

        List<Seat> newSeats = new ArrayList<>();
        for (RowSpec row : req.rows()) {
            for (int n = 1; n <= row.seatCount(); n++) {
                newSeats.add(new Seat(event, row.label(), n, row.category().trim(), row.priceCents()));
            }
        }
        seats.saveAll(newSeats);
        seats.flush();
        return detail(event.getId());
    }

    public List<OrganizerEventView> forOrganizer(Long organizerId) {
        List<Event> list = events.findByOrganizerIdOrderByStartsAtAsc(organizerId);
        Map<Long, SeatStats> stats = statsById(list);
        Map<Long, Long> checkedIn = list.isEmpty() ? Map.of() : bookings.countCheckedInByEvent(ids(list)).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return list.stream().map(e -> {
            SeatStats s = stats.getOrDefault(e.getId(), SeatStats.empty(e.getId()));
            return new OrganizerEventView(e.getId(), e.getTitle(), e.getVenue(), e.getCity(), e.getStartsAt(),
                    s.totalSeats(), s.bookedSeats(), s.availableSeats(), s.revenueCents(),
                    checkedIn.getOrDefault(e.getId(), 0L));
        }).toList();
    }

    private Map<Long, SeatStats> statsById(List<Event> list) {
        if (list.isEmpty()) return Map.of();
        return seats.statsForEvents(ids(list)).stream()
                .collect(Collectors.toMap(SeatStats::eventId, Function.identity()));
    }

    private static List<Long> ids(List<Event> list) {
        return list.stream().map(Event::getId).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
