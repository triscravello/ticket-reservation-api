package com.nextwork.ticketing.booking;
import java.util.List;
import java.util.UUID;
import com.nextwork.ticketing.booking.ApiModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class TicketController {
    private final ReservationService service;
    public TicketController(ReservationService service) { this.service = service; }
    @GetMapping("/events/{eventId}/seats")
    public List<SeatView> seats(@PathVariable UUID eventId) {
        return service.listSeats(eventId).stream().map(s -> SeatView.from(s, service.now())).toList();
    }
    @PostMapping("/events/{eventId}/holds")
    public ResponseEntity<HoldResponse> hold(@PathVariable UUID eventId, @RequestBody HoldRequest request) {
        var r = service.createHold(eventId, request.seatId(), request.buyerId());
        return new ResponseEntity<>(new HoldResponse(r.holdId(), r.seatId(), r.buyerId(), r.expiresAt()), HttpStatus.CREATED);
    }
    @PostMapping("/holds/{holdId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable UUID holdId) {
        var result = service.confirm(holdId);
        return result.expired()
                ? new ResponseEntity<>(new StatusResponse("EXPIRED"), HttpStatus.GONE)
                : new ResponseEntity<>(ReservationView.from(result.reservation()), HttpStatus.CREATED);
    }
    @DeleteMapping("/holds/{holdId}")
    public ResponseEntity<Void> release(@PathVariable UUID holdId) {
        service.release(holdId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/reservations/{id}")
    public ReservationView reservation(@PathVariable UUID id) { return ReservationView.from(service.getReservation(id)); }
}