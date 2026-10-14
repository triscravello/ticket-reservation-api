package com.nextwork.ticketing.lab;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import com.nextwork.ticketing.seat.Seat;
import com.nextwork.ticketing.seat.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class OptimisticHoldService {
    private final SeatRepository seats;
    private final Clock clock;
    private final CyclicBarrier barrier = new CyclicBarrier(2);
    public OptimisticHoldService(SeatRepository seats, Clock clock) { this.seats = seats; this.clock = clock; }
    @Transactional
    public UUID compete(UUID eventId, UUID seatId, String buyerId) {
        Instant now = clock.instant();
        Seat seat = seats.findOptimisticCandidate(eventId, seatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));
        if (!seat.isAvailableAt(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat unavailable");
        awaitOtherReader();
        seat.expireIfNeeded(now);
        UUID holdId = UUID.randomUUID();
        seat.placeHold(holdId, buyerId, now.plus(Duration.ofSeconds(30)));
        seats.saveAndFlush(seat);
        return holdId;
    }
    private void awaitOtherReader() {
        try { barrier.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (Exception e) { throw new IllegalStateException("Two readers did not reach the barrier", e); }
    }
}