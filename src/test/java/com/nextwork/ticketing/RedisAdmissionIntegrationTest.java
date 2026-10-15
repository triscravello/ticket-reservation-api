package com.nextwork.ticketing;

import static org.junit.jupiter.api.Assertions.*;
import com.nextwork.ticketing.admission.AdmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.*;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
@Testcontainers
@SpringBootTest(properties = {"app.admission.mode=redis", "app.admission.failure-policy=fail-closed", "app.admission.limit=1", "app.admission.lease-ttl=1s"})
class RedisAdmissionIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine3.24");
 @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.2-alpine")).withExposedPorts(6379);
 @DynamicPropertySource static void redisProperties(DynamicPropertyRegistry registry) {
  registry.add("spring.data.redis.host", REDIS::getHost);
  registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
 }
 @Autowired AdmissionService admission;
 @Test void leaseExpiresAndStaleReleaseCannotRemoveTheNewOwner() throws Exception {
  var first = admission.acquire();
  ResponseStatusException full = assertThrows(ResponseStatusException.class, admission::acquire); assertEquals(429, full.getStatusCode().value());
  Thread.sleep(1100); var second = admission.acquire(); first.close();
  ResponseStatusException stillFull = assertThrows(ResponseStatusException.class, admission::acquire); assertEquals(429, stillFull.getStatusCode().value());
  second.close(); try (var third = admission.acquire()) { assertEquals("redis", third.source()); }
 }
}