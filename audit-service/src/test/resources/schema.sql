CREATE DOMAIN IF NOT EXISTS JSONB AS JSON;

CREATE TABLE audit_events (
                              event_id UUID NOT NULL,
                              event_type VARCHAR(50) NOT NULL,
                              service_name VARCHAR(255) NOT NULL,
                              actor VARCHAR(255) NOT NULL,
                              entity_type VARCHAR(255) NOT NULL,
                              entity_id BIGINT,
                              payload JSONB,
                              message VARCHAR(1000),
                              timestamp TIMESTAMP NOT NULL,
                              version INTEGER NOT NULL,
                              PRIMARY KEY (event_id)
);