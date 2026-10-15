CREATE TABLE idempotency_records (
  idempotency_key VARCHAR(100) PRIMARY KEY,
  request_fingerprint VARCHAR(160) NOT NULL,
  response_status INTEGER,
  reservation_id UUID REFERENCES reservations(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMPTZ,
  CONSTRAINT ck_idempotency_completion CHECK (
    (response_status IS NULL AND reservation_id IS NULL AND completed_at IS NULL)
    OR (response_status IS NOT NULL AND completed_at IS NOT NULL)
  )
);
INSERT INTO seats (id, event_id, label, state)
SELECT md5('load-seat-' || n)::uuid,
       '20000000-0000-0000-0000-000000000001'::uuid,
       'L' || lpad(n::text, 4, '0'),
       'AVAILABLE'
FROM generate_series(1, 1000) AS n;