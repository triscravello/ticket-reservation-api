package com.nextwork.ticketing.idempotency;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
 @Id @Column(name = "idempotency_key", nullable = false, length = 100) private String key;
 @Column(name = "request_fingerprint", nullable = false, length = 160) private String requestFingerprint;
 @Column(name = "response_status") private Integer responseStatus;
 @Column(name = "reservation_id") private UUID reservationId;
 @Column(name = "created_at", nullable = false) private Instant createdAt;
 @Column(name = "completed_at") private Instant completedAt;
 protected IdempotencyRecord() {}
 public String getRequestFingerprint() { return requestFingerprint; }
 public Integer getResponseStatus() { return responseStatus; }
 public UUID getReservationId() { return reservationId; }
 public boolean isCompleted() { return responseStatus != null; }
 public void complete(int status, UUID completedReservationId, Instant time) {
  responseStatus = status; reservationId = completedReservationId; completedAt = time;
 }
}