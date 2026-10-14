package com.nextwork.ticketing.reservation;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
@Entity
@Table(name = "reservations")
public class Reservation {
    @Id private UUID id;
    @Column(name = "seat_id", nullable = false, unique = true) private UUID seatId;
    @Column(name = "buyer_id", nullable = false) private String buyerId;
    @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
    protected Reservation() {}
    public Reservation(UUID id, UUID seatId, String buyerId, Instant confirmedAt) {
        this.id = id; this.seatId = seatId; this.buyerId = buyerId; this.confirmedAt = confirmedAt;
    }
    public UUID getId() { return id; }
    public UUID getSeatId() { return seatId; }
    public String getBuyerId() { return buyerId; }
    public Instant getConfirmedAt() { return confirmedAt; }
}