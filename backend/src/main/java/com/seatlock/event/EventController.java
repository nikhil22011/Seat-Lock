package com.seatlock.event;

import com.seatlock.event.EventDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public List<EventSummary> upcoming() {
        return service.upcoming();
    }

    @GetMapping("/events/{id}")
    public EventDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/events/{id}/seats")
    public List<SeatView> seats(@PathVariable Long id) {
        return service.seatMap(id);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateEventRequest req) {
        return service.create(Long.valueOf(jwt.getSubject()), req);
    }

    @GetMapping("/organizer/events")
    @PreAuthorize("hasRole('ORGANIZER')")
    public List<OrganizerEventView> myEvents(@AuthenticationPrincipal Jwt jwt) {
        return service.forOrganizer(Long.valueOf(jwt.getSubject()));
    }
}
