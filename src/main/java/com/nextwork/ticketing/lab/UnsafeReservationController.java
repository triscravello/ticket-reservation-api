package com.nextwork.ticketing.lab;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/lab/unsafe-reservations")
public class UnsafeReservationController {
    private final UnsafeReservationService service;
    public UnsafeReservationController(UnsafeReservationService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<Response> reserve(@RequestBody Request request) {
        var r = service.reserve(request.seatId(), request.buyerId());
        return new ResponseEntity<>(new Response(r.getId(), r.getSeatId(), r.getBuyerId()), HttpStatus.CREATED);
    }
    public record Request(UUID seatId, String buyerId) {}
    public record Response(UUID reservationId, UUID seatId, String buyerId) {}
}