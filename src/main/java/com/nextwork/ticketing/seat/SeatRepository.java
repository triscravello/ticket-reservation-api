package com.nextwork.ticketing.seat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByEventIdOrderByLabel(UUID eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.eventId=:eventId and s.id=:seatId")
    Optional<Seat> findForUpdate(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.holdId=:holdId")
    Optional<Seat> findByHoldIdForUpdate(@Param("holdId") UUID holdId);
    @Query("select s from Seat s where s.eventId=:eventId and s.id=:seatId")
    Optional<Seat> findOptimisticCandidate(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);
}