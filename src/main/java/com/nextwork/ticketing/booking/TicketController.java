package com.nextwork.ticketing.booking;
import java.util.*;
import com.nextwork.ticketing.booking.ApiModels.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class TicketController {
 private final ReservationService service; private final ConfirmationFacade confirmations;
 public TicketController(ReservationService service, ConfirmationFacade confirmations) { this.service = service; this.confirmations = confirmations; }
 @GetMapping("/events/{eventId}/seats") public List<SeatView> seats(@PathVariable UUID eventId) {
  return service.listSeats(eventId).stream().map(s -> SeatView.from(s, service.now())).toList();
 }
 @PostMapping("/events/{eventId}/holds") public ResponseEntity<HoldResponse> hold(@PathVariable UUID eventId, @RequestBody HoldRequest request) {
  var result = service.createHold(eventId, request.seatId(), request.buyerId());
  return new ResponseEntity<>(new HoldResponse(result.holdId(), result.seatId(), result.buyerId(), result.expiresAt()), HttpStatus.CREATED);
 }
 @PostMapping("/holds/{holdId}/confirm") public ResponseEntity<?> confirm(@PathVariable UUID holdId,
   @RequestHeader(value = "Idempotency-Key", required = false) String suppliedKey) {
  String key = suppliedKey == null || suppliedKey.isBlank() ? "legacy-" + UUID.randomUUID() : suppliedKey;
  var envelope = confirmations.confirm(holdId, key); var result = envelope.result();
  var builder = ResponseEntity.status(result.expired() ? HttpStatus.GONE : HttpStatus.CREATED)
   .header("X-Admission-Source", envelope.admissionSource())
   .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
  return result.expired() ? builder.body(new StatusResponse("EXPIRED")) : builder.body(ReservationView.from(result.reservation()));
 }
 @DeleteMapping("/holds/{holdId}") public ResponseEntity<Void> release(@PathVariable UUID holdId) { service.release(holdId); return ResponseEntity.noContent().build(); }
 @GetMapping("/reservations/{id}") public ReservationView reservation(@PathVariable UUID id) { return ReservationView.from(service.getReservation(id)); }
}