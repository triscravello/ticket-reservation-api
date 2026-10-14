package com.nextwork.ticketing.lab;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class UnsafeReservationService {
    private final UnsafeReservationRepository repository;
    private final Clock clock;
    private final CyclicBarrier barrier = new CyclicBarrier(2);
    public UnsafeReservationService(UnsafeReservationRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }
    @Transactional
    public UnsafeReservation reserve(UUID seatId, String buyerId) {
        if (repository.countBySeatId(seatId) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat already reserved");
        awaitBuyer();
        return repository.saveAndFlush(new UnsafeReservation(UUID.randomUUID(), seatId, buyerId, clock.instant()));
    }
    private void awaitBuyer() {
        try { barrier.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (Exception e) { throw new IllegalStateException("Two buyers did not reach the race window", e); }
    }
}