package com.nextwork.ticketing.seat;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
@Entity
@Table(name = "seats")
public class Seat {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(nullable = false) private String label;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SeatState state;
    @Column(name = "hold_id") private UUID holdId;
    @Column(name = "held_by") private String heldBy;
    @Column(name = "hold_expires_at") private Instant holdExpiresAt;
    @Version @Column(nullable = false) private long version;
    protected Seat() {}
    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public SeatState getState() { return state; }
    public String getHeldBy() { return heldBy; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public long getVersion() { return version; }
    public boolean isExpired(Instant now) {
        return state == SeatState.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }
    public boolean isAvailableAt(Instant now) { return state == SeatState.AVAILABLE || isExpired(now); }
    public void expireIfNeeded(Instant now) { if (isExpired(now)) release(); }
    public void placeHold(UUID newHoldId, String buyerId, Instant expiresAt) {
        state = SeatState.HELD; holdId = newHoldId; heldBy = buyerId; holdExpiresAt = expiresAt;
    }
    public void confirm() { state = SeatState.SOLD; clearHoldFields(); }
    public void release() { state = SeatState.AVAILABLE; clearHoldFields(); }
    private void clearHoldFields() { holdId = null; heldBy = null; holdExpiresAt = null; }
}