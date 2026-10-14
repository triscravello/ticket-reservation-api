package com.nextwork.ticketing.lab;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UnsafeReservationRepository extends JpaRepository<UnsafeReservation, UUID> {
    long countBySeatId(UUID seatId);
}