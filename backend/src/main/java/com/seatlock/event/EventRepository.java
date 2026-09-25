package com.seatlock.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStartsAtAfterOrderByStartsAtAsc(Instant after);

    List<Event> findByOrganizerIdOrderByStartsAtAsc(Long organizerId);

    @Query("select e from Event e join fetch e.organizer where e.id = :id")
    Optional<Event> findWithOrganizer(@Param("id") Long id);
}
