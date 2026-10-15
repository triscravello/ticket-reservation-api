package com.nextwork.ticketing;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TicketReservationIntegrationTest.TestClockConfiguration.class)
class TicketReservationIntegrationTest {
    static final UUID EVENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID SEAT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final Instant START = Instant.parse("2026-10-13T12:00:00Z");
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24");
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    final HttpClient client = HttpClient.newHttpClient();
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM idempotency_records"); // Clear referencing rows first!
        jdbc.update("DELETE FROM reservations");
        jdbc.update("UPDATE seats SET state='AVAILABLE', hold_id=NULL, held_by=NULL, hold_expires_at=NULL, version=0");
    }
    @Test void unsafePathDoubleBooksOneSeat() throws Exception {
        var responses = posts(new Spec("/api/lab/unsafe-reservations", body("buyer-a")),
                new Spec("/api/lab/unsafe-reservations", body("buyer-b")));
        assertEquals(List.of(201, 201), statuses(responses));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM unsafe_reservations WHERE seat_id=?", Integer.class, SEAT);
        assertEquals(2, count); System.out.println("Unsafe reservations for one seat: " + count);
    }
    @Test void pessimisticLockAllowsOneHoldWinner() throws Exception {
        String path = "/api/events/" + EVENT + "/holds";
        var responses = posts(new Spec(path, body("buyer-a")), new Spec(path, body("buyer-b")));
        assertEquals(List.of(201, 409), statuses(responses));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM seats WHERE id=? AND state='HELD'", Integer.class, SEAT);
        assertEquals(1, count); System.out.println("Safe hold winners: " + count);
    }
    @Test void concurrentConfirmationCreatesOneReservation() throws Exception {
        UUID holdId = uuid(post("/api/events/" + EVENT + "/holds", body("buyer-a")).body(), "holdId");
        var responses = posts(new Spec("/api/holds/" + holdId + "/confirm", "{}"),
                new Spec("/api/holds/" + holdId + "/confirm", "{}"));
        assertEquals(List.of(201, 404), statuses(responses));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE seat_id=?", Integer.class, SEAT));
    }
    @Test void expiredHoldCannotConfirmWhileNextBuyerReclaims() throws Exception {
        UUID holdId = uuid(post("/api/events/" + EVENT + "/holds", body("buyer-a")).body(), "holdId");
        clock.advance(Duration.ofSeconds(31));
        var responses = posts(new Spec("/api/holds/" + holdId + "/confirm", "{}"),
                new Spec("/api/events/" + EVENT + "/holds", body("buyer-c")));
        var codes = statuses(responses);
        assertEquals(201, codes.get(0));
        assertTrue(codes.get(1) == 404 || codes.get(1) == 410);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE seat_id=?", Integer.class, SEAT));
        assertEquals("buyer-c", jdbc.queryForObject("SELECT held_by FROM seats WHERE id=?", String.class, SEAT));
    }
    List<HttpResponse<String>> posts(Spec a, Spec b) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<HttpResponse<String>> x = executor.submit(() -> post(a.path(), a.body()));
            Future<HttpResponse<String>> y = executor.submit(() -> post(b.path(), b.body()));
            return List.of(x.get(), y.get());
        }
    }
    HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    static List<Integer> statuses(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::statusCode).sorted().toList();
    }
    static String body(String buyer) {
        return "{\"seatId\":\"%s\",\"buyerId\":\"%s\"}".formatted(SEAT, buyer);
    }
    static UUID uuid(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new IllegalArgumentException(json);
        return UUID.fromString(m.group(1));
    }
    record Spec(String path, String body) {}
    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(START); }
    }
    static final class MutableClock extends Clock {
        Instant current;
        MutableClock(Instant current) { this.current = current; }
        void advance(Duration duration) { current = current.plus(duration); }
        void reset() { current = START; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return current; }
    }
}