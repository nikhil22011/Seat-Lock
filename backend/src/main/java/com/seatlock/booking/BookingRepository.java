package com.seatlock.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * SELECT ... FOR UPDATE. Payment, cancel and expiry all take this lock first, so they can
     * never interleave on the same booking (e.g. the expiry job can't free seats mid-payment).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("select b from Booking b join fetch b.event join fetch b.user where b.id = :id")
    Optional<Booking> findWithDetails(@Param("id") Long id);

    @Query("select b from Booking b join fetch b.event where b.user.id = :userId order by b.createdAt desc")
    List<Booking> findForUser(@Param("userId") Long userId);

    List<Booking> findByUserIdAndEventIdAndStatus(Long userId, Long eventId, BookingStatus status);

    @Query("select b.id from Booking b where b.status = com.seatlock.booking.BookingStatus.HELD and b.expiresAt < :now")
    List<Long> findExpiredHoldIds(@Param("now") Instant now);

    /** Atomic check-in: only succeeds once per ticket, even if two gate scanners scan it together. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Booking b set b.checkedInAt = :now
             where b.id = :id
               and b.status = com.seatlock.booking.BookingStatus.CONFIRMED
               and b.checkedInAt is null
            """)
    int markCheckedIn(@Param("id") Long id, @Param("now") Instant now);

    @Query("""
            select b.event.id, count(b) from Booking b
             where b.event.id in :eventIds and b.checkedInAt is not null
             group by b.event.id
            """)
    List<Object[]> countCheckedInByEvent(@Param("eventIds") Collection<Long> eventIds);
}
