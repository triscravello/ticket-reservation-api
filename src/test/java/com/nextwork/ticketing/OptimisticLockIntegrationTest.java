package com.nextwork.ticketing;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.nextwork.ticketing.lab.OptimisticHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
@Testcontainers
@SpringBootTest
@Import(OptimisticLockIntegrationTest.OptimisticClockConfiguration.class)
class OptimisticLockIntegrationTest {
    static final UUID EVENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID SEAT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24");
    @Autowired JdbcTemplate jdbc;
    @Autowired OptimisticHoldService service;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM reservations");
        jdbc.update("UPDATE seats SET state='AVAILABLE', hold_id=NULL, held_by=NULL, hold_expires_at=NULL, version=0");
    }
    @Test void optimisticLockProducesOneWinnerAndOneConflict() throws Exception {
        int successes = 0;
        int conflicts = 0;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<UUID> first = executor.submit(() -> service.compete(EVENT, SEAT, "buyer-a"));
            Future<UUID> second = executor.submit(() -> service.compete(EVENT, SEAT, "buyer-b"));
            for (Future<UUID> result : List.of(first, second)) {
                try { result.get(); successes++; }
                catch (ExecutionException e) {
                    assertTrue(hasCause(e, ObjectOptimisticLockingFailureException.class));
                    conflicts++;
                }
            }
        }
        Long version = jdbc.queryForObject("SELECT version FROM seats WHERE id=?", Long.class, SEAT);
        assertEquals(1, successes);
        assertEquals(1, conflicts);
        assertEquals(1L, version);
        System.out.println("Optimistic results: one winner, one ObjectOptimisticLockingFailureException, version=" + version);
    }
    static boolean hasCause(Throwable error, Class<?> type) {
        for (Throwable current = error; current != null; current = current.getCause())
            if (type.isInstance(current)) return true;
        return false;
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class OptimisticClockConfiguration {
        @Bean @Primary Clock optimisticClock() {
            return Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}