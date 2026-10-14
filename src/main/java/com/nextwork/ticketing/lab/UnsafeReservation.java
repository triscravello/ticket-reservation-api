package com.nextwork.ticketing.lab;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "unsafe_reservations")
public class UnsafeReservation {
    @Id 
    private UUID id;

    @Column(name = "seat_id", nullable = false) 
    private UUID seatId;

    @Column(name = "buyer_id", nullable = false) 
    private String buyerId;

    @Column(name = "created_at", nullable = false) 
    private Instant createdAt;

    protected UnsafeReservation() {}

    public UnsafeReservation(UUID id, UUID seatId, String buyerId, Instant createdAt) {
        this.id = id; 
        this.seatId = seatId; 
        this.buyerId = buyerId; 
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getSeatId() { return seatId; }
    public String getBuyerId() { return buyerId; }
}