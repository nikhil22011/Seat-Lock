package com.seatlock.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderByRowLabelAscNumberAsc(Long eventId);

    List<Seat> findByEventIdAndIdIn(Long eventId, Collection<Long> ids);

    List<Seat> findByBookingIdInOrderByRowLabelAscNumberAsc(Collection<Long> bookingIds);

    /**
     * THE core of SeatLock: an atomic "compare-and-set" on each seat row.
     * <p>
     * The WHERE clause only matches seats that are free (or whose hold has expired). The database
     * row-locks each matched row during the UPDATE, so if two users race for the same seat, the
     * second UPDATE re-checks the condition after the first commits and matches 0 rows.
     * The caller compares the returned count to the number requested and rolls back on a mismatch,
     * so a user either gets ALL their seats or NONE.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.seatlock.event.SeatStatus.HELD,
                   s.bookingId = :bookingId,
                   s.holdExpiresAt = :expiresAt,
                   s.version = s.version + 1
             where s.id in :seatIds
               and s.event.id = :eventId
               and (s.status = com.seatlock.event.SeatStatus.AVAILABLE
                    or (s.status = com.seatlock.event.SeatStatus.HELD and s.holdExpiresAt < :now))
            """)
    int holdSeats(@Param("eventId") Long eventId,
                  @Param("seatIds") Collection<Long> seatIds,
                  @Param("bookingId") Long bookingId,
                  @Param("now") Instant now,
                  @Param("expiresAt") Instant expiresAt);

    /** Turns this booking's held seats into sold seats. Returns how many were still held by it. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.seatlock.event.SeatStatus.BOOKED,
                   s.holdExpiresAt = null,
                   s.version = s.version + 1
             where s.bookingId = :bookingId
               and s.status = com.seatlock.event.SeatStatus.HELD
            """)
    int confirmSeats(@Param("bookingId") Long bookingId);

    /** Frees seats still held by this booking (a seat already re-held by someone else is untouched). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.seatlock.event.SeatStatus.AVAILABLE,
                   s.bookingId = null,
                   s.holdExpiresAt = null,
                   s.version = s.version + 1
             where s.bookingId = :bookingId
               and s.status = com.seatlock.event.SeatStatus.HELD
            """)
    int releaseSeats(@Param("bookingId") Long bookingId);

    @Query("select s.id from Seat s where s.bookingId = :bookingId")
    List<Long> findIdsByBookingId(@Param("bookingId") Long bookingId);

    @Query("""
            select new com.seatlock.event.SeatStats(
                     s.event.id,
                     min(s.priceCents),
                     max(s.priceCents),
                     count(s),
                     sum(case when s.status = com.seatlock.event.SeatStatus.AVAILABLE then 1 else 0 end),
                     sum(case when s.status = com.seatlock.event.SeatStatus.BOOKED then 1 else 0 end),
                     sum(case when s.status = com.seatlock.event.SeatStatus.BOOKED then s.priceCents else 0 end))
              from Seat s
             where s.event.id in :eventIds
             group by s.event.id
            """)
    List<SeatStats> statsForEvents(@Param("eventIds") Collection<Long> eventIds);
}
