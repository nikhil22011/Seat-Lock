package com.seatlock.booking;

import com.seatlock.booking.BookingDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/events/{eventId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView hold(@AuthenticationPrincipal Jwt jwt, @PathVariable Long eventId,
                            @Valid @RequestBody HoldRequest req) {
        return service.hold(userId(jwt), eventId, req.seatIds());
    }

    @PostMapping("/bookings/{id}/pay")
    public PayResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                           @RequestBody(required = false) PayRequest req) {
        return service.pay(userId(jwt), id, idempotencyKey, req == null ? null : req.simulate());
    }

    @DeleteMapping("/bookings/{id}")
    public BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.cancel(userId(jwt), id);
    }

    @GetMapping("/bookings/me")
    public List<BookingView> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.forUser(userId(jwt));
    }

    @GetMapping("/bookings/{id}")
    public BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(userId(jwt), id);
    }

    @PostMapping("/checkin")
    @PreAuthorize("hasRole('ORGANIZER')")
    public CheckInResult checkIn(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckInRequest req) {
        return service.checkIn(userId(jwt), req.code());
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
