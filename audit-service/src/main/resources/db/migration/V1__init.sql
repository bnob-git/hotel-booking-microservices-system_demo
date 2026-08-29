CREATE TABLE audit_events (
    event_id     UUID          PRIMARY KEY,
    event_type   VARCHAR(255)  NOT NULL,
    service_name VARCHAR(255)  NOT NULL,
    actor        VARCHAR(255)  NOT NULL,
    entity_type  VARCHAR(255)  NOT NULL,
    entity_id    BIGINT,
    payload      JSONB,
    message      VARCHAR(1000),
    "timestamp"  TIMESTAMP(6)  NOT NULL,
    version      INTEGER       NOT NULL
);

CREATE INDEX idx_audit_events_timestamp ON audit_events ("timestamp");
CREATE INDEX idx_audit_events_event_type ON audit_events (event_type);
