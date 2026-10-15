package com.nextwork.ticketing.booking;
import java.time.*;
import java.util.*;
import com.nextwork.ticketing.idempotency.*;
import com.nextwork.ticketing.reservation.*;
import com.nextwork.ticketing.seat.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ReservationService {
 private static final Duration HOLD_DURATION = Duration.ofSeconds(30);
 private final SeatRepository seats; private final ReservationRepository reservations;
 private final IdempotencyRecordRepository idempotency; private final Clock clock;
 public ReservationService(SeatRepository seats, ReservationRepository reservations, IdempotencyRecordRepository idempotency, Clock clock) {
  this.seats = seats; this.reservations = reservations; this.idempotency = idempotency; this.clock = clock;
 }
 @Transactional(readOnly = true) public List<Seat> listSeats(UUID eventId) { return seats.findByEventIdOrderByLabel(eventId); }
 @Transactional public HoldResult createHold(UUID eventId, UUID seatId, String buyerId) {
  Instant now = clock.instant();
  Seat seat = seats.findForUpdate(eventId, seatId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));
  if (!seat.isAvailableAt(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is not available");
  seat.expireIfNeeded(now); UUID holdId = UUID.randomUUID(); Instant expiresAt = now.plus(HOLD_DURATION);
  seat.placeHold(holdId, buyerId, expiresAt); seats.save(seat);
  return new HoldResult(holdId, seatId, buyerId, expiresAt);
 }
 @Transactional public ConfirmationResult confirm(UUID holdId, String key) {
  if (key == null || key.isBlank() || key.length() > 100)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 100 characters");
  Instant now = clock.instant(); String fingerprint = "confirm:" + holdId;
  boolean claimed = idempotency.insertIfAbsent(key, fingerprint) == 1;
  IdempotencyRecord record = idempotency.findById(key).orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));
  if (!fingerprint.equals(record.getRequestFingerprint()))
   throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was used for another request");
  if (record.isCompleted()) {
   if (record.getResponseStatus() == 410) return new ConfirmationResult(null, true, true);
   Reservation replay = reservations.findById(record.getReservationId()).orElseThrow(() -> new IllegalStateException("Completed reservation is missing"));
   return new ConfirmationResult(replay, false, true);
  }
  if (!claimed) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency request is still in progress");
  Seat seat = seats.findByHoldIdForUpdate(holdId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hold not found"));
  if (seat.isExpired(now)) {
   seat.release(); seats.save(seat); record.complete(410, null, now); idempotency.save(record);
   return new ConfirmationResult(null, true, false);
  }
  Reservation reservation = new Reservation(UUID.randomUUID(), seat.getId(), seat.getHeldBy(), now);
  reservations.save(reservation); seat.confirm(); seats.save(seat);
  record.complete(201, reservation.getId(), now); idempotency.save(record);
  return new ConfirmationResult(reservation, false, false);
 }
 @Transactional public void release(UUID holdId) {
  Seat seat = seats.findByHoldIdForUpdate(holdId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hold not found"));
  seat.release(); seats.save(seat);
 }
 @Transactional(readOnly = true) public Reservation getReservation(UUID id) {
  return reservations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
 }
 public Instant now() { return clock.instant(); }
 public record HoldResult(UUID holdId, UUID seatId, String buyerId, Instant expiresAt) {}
 public record ConfirmationResult(Reservation reservation, boolean expired, boolean replayed) {}
}