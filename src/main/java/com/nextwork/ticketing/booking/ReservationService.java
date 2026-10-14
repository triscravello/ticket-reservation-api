package com.nextwork.ticketing.booking;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.nextwork.ticketing.reservation.Reservation;
import com.nextwork.ticketing.reservation.ReservationRepository;
import com.nextwork.ticketing.seat.Seat;
import com.nextwork.ticketing.seat.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ReservationService {
    private static final Duration HOLD_DURATION = Duration.ofSeconds(30);
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final Clock clock;
    public ReservationService(SeatRepository seats, ReservationRepository reservations, Clock clock) {
        this.seats = seats; this.reservations = reservations; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<Seat> listSeats(UUID eventId) { return seats.findByEventIdOrderByLabel(eventId); }
    @Transactional
    public HoldResult createHold(UUID eventId, UUID seatId, String buyerId) {
        Instant now = clock.instant();
        Seat seat = seats.findForUpdate(eventId, seatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));
        if (!seat.isAvailableAt(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is not available");
        seat.expireIfNeeded(now);
        UUID holdId = UUID.randomUUID();
        Instant expiresAt = now.plus(HOLD_DURATION);
        seat.placeHold(holdId, buyerId, expiresAt);
        seats.save(seat);
        return new HoldResult(holdId, seatId, buyerId, expiresAt);
    }
    @Transactional
    public ConfirmationResult confirm(UUID holdId) {
        Instant now = clock.instant();
        Seat seat = seats.findByHoldIdForUpdate(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hold not found"));
        if (seat.isExpired(now)) {
            seat.release(); seats.save(seat); return new ConfirmationResult(null, true);
        }
        Reservation reservation = new Reservation(UUID.randomUUID(), seat.getId(), seat.getHeldBy(), now);
        reservations.save(reservation);
        seat.confirm(); seats.save(seat);
        return new ConfirmationResult(reservation, false);
    }
    @Transactional
    public void release(UUID holdId) {
        Seat seat = seats.findByHoldIdForUpdate(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hold not found"));
        seat.release(); seats.save(seat);
    }
    @Transactional(readOnly = true)
    public Reservation getReservation(UUID id) {
        return reservations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }
    public Instant now() { return clock.instant(); }
    public record HoldResult(UUID holdId, UUID seatId, String buyerId, Instant expiresAt) {}
    public record ConfirmationResult(Reservation reservation, boolean expired) {}
}