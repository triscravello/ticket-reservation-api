package com.nextwork.ticketing;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyIntegrationTest {
 static final UUID EVENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
 static final UUID A1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
 static final UUID A2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine3.24");
 @LocalServerPort int port; @Autowired JdbcTemplate jdbc; final HttpClient client = HttpClient.newHttpClient();
 @BeforeEach void reset() {
  jdbc.update("DELETE FROM idempotency_records"); jdbc.update("DELETE FROM reservations");
  jdbc.update("UPDATE seats SET state='AVAILABLE', hold_id=NULL, held_by=NULL, hold_expires_at=NULL, version=0");
 }
 @Test void concurrentSameKeyReplaysOnePurchase() throws Exception {
  UUID hold = createHold(A1, "buyer-a"); String path = "/api/holds/" + hold + "/confirm";
  List<HttpResponse<String>> responses = concurrent(path, "same-key");
  assertEquals(List.of(201, 201), responses.stream().map(HttpResponse::statusCode).sorted().toList());
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE seat_id=?", Integer.class, A1));
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key='same-key'", Integer.class));
 }
 @Test void sameKeyWithDifferentFingerprintIsRejected() throws Exception {
  UUID first = createHold(A1, "buyer-a"), second = createHold(A2, "buyer-b");
  assertEquals(201, post("/api/holds/" + first + "/confirm", "shared-key").statusCode());
  assertEquals(409, post("/api/holds/" + second + "/confirm", "shared-key").statusCode());
  assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
 }
 @Test void failedTransactionDoesNotConsumeTheKey() throws Exception {
  String key = "rollback-safe";
  assertEquals(404, post("/api/holds/" + UUID.randomUUID() + "/confirm", key).statusCode());
  assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key=?", Integer.class, key));
  assertEquals(201, post("/api/holds/" + createHold(A1, "buyer-a") + "/confirm", key).statusCode());
 }
 UUID createHold(UUID seat, String buyer) throws Exception {
  String body = "{\"seatId\":\"%s\",\"buyerId\":\"%s\"}".formatted(seat, buyer);
  HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/events/" + EVENT + "/holds"))
   .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
  Matcher matcher = Pattern.compile("\"holdId\":\"([^\"]+)\"").matcher(response.body()); assertTrue(matcher.find());
  return UUID.fromString(matcher.group(1));
 }
 HttpResponse<String> post(String path, String key) throws Exception {
  HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
   .header("Content-Type", "application/json").header("Idempotency-Key", key)
   .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
  return client.send(request, HttpResponse.BodyHandlers.ofString());
 }
 List<HttpResponse<String>> concurrent(String path, String key) throws Exception {
  try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
   Future<HttpResponse<String>> first = executor.submit(() -> post(path, key));
   Future<HttpResponse<String>> second = executor.submit(() -> post(path, key));
   return List.of(first.get(), second.get());
  }
 }
}