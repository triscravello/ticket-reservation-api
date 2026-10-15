package com.nextwork.ticketing.idempotency;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
 @Modifying
 @Query(value = """
   INSERT INTO idempotency_records (idempotency_key, request_fingerprint)
   VALUES (:key, :fingerprint)
   ON CONFLICT (idempotency_key) DO NOTHING
   """, nativeQuery = true)
 int insertIfAbsent(@Param("key") String key, @Param("fingerprint") String fingerprint);
}