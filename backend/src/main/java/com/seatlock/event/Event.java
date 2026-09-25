package com.seatlock.event;

import com.seatlock.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "events", indexes = @Index(name = "idx_events_starts_at", columnList = "startsAt"))
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 150)
    private String venue;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User organizer;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Event() {}

    public Event(String title, String description, String venue, String city, Instant startsAt,
                 String imageUrl, User organizer) {
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.city = city;
        this.startsAt = startsAt;
        this.imageUrl = imageUrl;
        this.organizer = organizer;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVenue() { return venue; }
    public String getCity() { return city; }
    public Instant getStartsAt() { return startsAt; }
    public String getImageUrl() { return imageUrl; }
    public User getOrganizer() { return organizer; }
    public Instant getCreatedAt() { return createdAt; }
}
