package com.nextwork.ticketing.booking;
import java.time.Duration;
import java.util.UUID;
import com.nextwork.ticketing.admission.AdmissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ConfirmationFacade {
 private final AdmissionService admission; private final ReservationService reservations; private final Duration holdDelay;
 public ConfirmationFacade(AdmissionService admission, ReservationService reservations,
   @Value("${app.admission.hold-delay:0ms}") Duration holdDelay) {
  this.admission = admission; this.reservations = reservations; this.holdDelay = holdDelay;
 }
 public ConfirmationEnvelope confirm(UUID holdId, String key) {
  try (var lease = admission.acquire()) {
   try { Thread.sleep(holdDelay.toMillis()); }
   catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted", interrupted);
   }
   return new ConfirmationEnvelope(reservations.confirm(holdId, key), lease.source());
  }
 }
 public record ConfirmationEnvelope(ReservationService.ConfirmationResult result, String admissionSource) {}
}