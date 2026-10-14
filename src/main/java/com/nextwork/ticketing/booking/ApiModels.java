package com.nextwork.ticketing.booking;
import java.time.Instant;
import java.util.UUID;
import com.nextwork.ticketing.reservation.Reservation;
import com.nextwork.ticketing.seat.Seat;
import com.nextwork.ticketing.seat.SeatState;
public final class ApiModels {
    private ApiModels() {}
    public record HoldRequest(UUID seatId, String buyerId) {}
    public record HoldResponse(UUID holdId, UUID seatId, String buyerId, Instant expiresAt) {}
    public record SeatView(UUID id, String label, String state, Instant holdExpiresAt) {
        static SeatView from(Seat seat, Instant now) {
            boolean available = seat.isAvailableAt(now);
            return new SeatView(seat.getId(), seat.getLabel(),
                    available ? SeatState.AVAILABLE.name() : seat.getState().name(),
                    available ? null : seat.getHoldExpiresAt());
        }
    }
    public record ReservationView(UUID reservationId, UUID seatId, String buyerId, Instant confirmedAt) {
        static ReservationView from(Reservation r) {
            return new ReservationView(r.getId(), r.getSeatId(), r.getBuyerId(), r.getConfirmedAt());
        }
    }
    public record StatusResponse(String status) {}
}