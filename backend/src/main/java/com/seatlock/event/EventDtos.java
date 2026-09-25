package com.seatlock.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class EventDtos {

    private EventDtos() {}

    public record EventSummary(
            Long id, String title, String venue, String city, Instant startsAt, String imageUrl,
            int minPriceCents, int maxPriceCents, long totalSeats, long availableSeats
    ) {
        static EventSummary of(Event e, SeatStats s) {
            return new EventSummary(e.getId(), e.getTitle(), e.getVenue(), e.getCity(), e.getStartsAt(),
                    e.getImageUrl(), s.minPriceCents(), s.maxPriceCents(), s.totalSeats(), s.availableSeats());
        }
    }

    public record PriceCategory(String category, int priceCents) {}

    public record EventDetail(
            Long id, String title, String description, String venue, String city, Instant startsAt,
            String imageUrl, String organizerName, List<PriceCategory> categories,
            long totalSeats, long availableSeats
    ) {}

    public record SeatView(Long id, String row, int number, String category, int priceCents, SeatStatus status) {
        public static SeatView of(Seat s, Instant now) {
            return new SeatView(s.getId(), s.getRowLabel(), s.getNumber(), s.getCategory(), s.getPriceCents(),
                    s.effectiveStatus(now));
        }
    }

    public record RowSpec(
            @NotBlank @Pattern(regexp = "[A-Z]{1,2}", message = "must be 1-2 capital letters") String label,
            @Min(1) @Max(40) int seatCount,
            @NotBlank @Size(max = 30) String category,
            @Min(0) @Max(10_000_000) int priceCents
    ) {}

    public record CreateEventRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 150) String venue,
            @NotBlank @Size(max = 80) String city,
            @NotNull @Future Instant startsAt,
            @Size(max = 500) String imageUrl,
            @NotEmpty @Size(max = 26) List<@Valid RowSpec> rows
    ) {}

    /** What an organizer sees on their dashboard. */
    public record OrganizerEventView(
            Long id, String title, String venue, String city, Instant startsAt,
            long totalSeats, long bookedSeats, long availableSeats, long revenueCents, long checkedIn
    ) {}
}
