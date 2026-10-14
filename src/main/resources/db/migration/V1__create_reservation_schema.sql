CREATE TABLE seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    label VARCHAR(20) NOT NULL,
    state VARCHAR(16) NOT NULL,
    hold_id UUID UNIQUE,
    held_by VARCHAR(100),
    hold_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_event_seat UNIQUE (event_id, label),
    CONSTRAINT ck_seat_state CHECK (state IN ('AVAILABLE', 'HELD', 'SOLD')),
    CONSTRAINT ck_hold_fields CHECK (
        (state = 'HELD' AND hold_id IS NOT NULL AND held_by IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR (state IN ('AVAILABLE', 'SOLD') AND hold_id IS NULL AND held_by IS NULL AND hold_expires_at IS NULL)
    )
);
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    seat_id UUID NOT NULL UNIQUE REFERENCES seats(id),
    buyer_id VARCHAR(100) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE unsafe_reservations (
    id UUID PRIMARY KEY,
    seat_id UUID NOT NULL REFERENCES seats(id),
    buyer_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO seats (id, event_id, label, state) VALUES
('10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'A1', 'AVAILABLE'),
('10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'A2', 'AVAILABLE');