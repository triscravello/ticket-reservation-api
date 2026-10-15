package com.nextwork.ticketing.admission;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class AdmissionService {
 private static final String KEY = "admission:confirm";
 private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
   local time = redis.call('TIME')
   local now = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)
   redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
   if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end
   redis.call('ZADD', KEYS[1], now + tonumber(ARGV[1]), ARGV[3])
   redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
   return 1
   """, Long.class);
 private static final RedisScript<Long> RELEASE = RedisScript.of("return redis.call('ZREM', KEYS[1], ARGV[1])", Long.class);
 private final StringRedisTemplate redis;
 private final String mode;
 private final String failurePolicy;
 private final int limit;
 private final long leaseTtlMillis;
 private final Semaphore localSemaphore;
 public AdmissionService(StringRedisTemplate redis,
   @Value("${app.admission.mode:off}") String mode,
   @Value("${app.admission.failure-policy:fail-open}") String failurePolicy,
   @Value("${app.admission.limit:8}") int limit,
   @Value("${app.admission.lease-ttl:2s}") Duration leaseTtl) {
  this.redis = redis; this.mode = mode; this.failurePolicy = failurePolicy; this.limit = limit;
  leaseTtlMillis = leaseTtl.toMillis(); localSemaphore = new Semaphore(limit);
 }
 public Lease acquire() {
  return switch (mode.toLowerCase(Locale.ROOT)) {
   case "off" -> Lease.noop("off");
   case "local" -> acquireLocal();
   case "redis" -> acquireRedis();
   default -> throw new IllegalStateException("Unknown admission mode: " + mode);
  };
 }
 private Lease acquireLocal() {
  if (!localSemaphore.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Local admission limit reached");
  return new Lease("local", localSemaphore::release);
 }
 private Lease acquireRedis() {
  String token = UUID.randomUUID().toString();
  long keyTtlSeconds = Math.max(1, ((leaseTtlMillis + 999) / 1000) * 2);
  try {
   Long admitted = redis.execute(ACQUIRE, List.of(KEY), Long.toString(leaseTtlMillis), Integer.toString(limit), token, Long.toString(keyTtlSeconds));
   if (!Long.valueOf(1).equals(admitted)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Redis admission limit reached");
   return new Lease("redis", () -> releaseRedis(token));
  } catch (DataAccessException failure) {
   if ("fail-open".equalsIgnoreCase(failurePolicy)) return Lease.noop("fail-open");
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admission service unavailable", failure);
  }
 }
 private void releaseRedis(String token) {
  try { redis.execute(RELEASE, List.of(KEY), token); } catch (DataAccessException ignored) { }
 }
 public static final class Lease implements AutoCloseable {
  private final String source; private final Runnable release; private final AtomicBoolean closed = new AtomicBoolean();
  private Lease(String source, Runnable release) { this.source = source; this.release = release; }
  static Lease noop(String source) { return new Lease(source, () -> {}); }
  public String source() { return source; }
  public void close() { if (closed.compareAndSet(false, true)) release.run(); }
 }
}