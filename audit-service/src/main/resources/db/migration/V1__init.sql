-- Initial audit-service schema.
--
-- Reproduces exactly the schema Hibernate generated for com.hotel.audit.entity.AuditEvent
-- (taken from `pg_dump -s` of a `ddl-auto: create` run), so switching to Flyway is
-- behavior-preserving. audit-service seeds no data, so there is nothing else to replay.
--
-- The primary key on event_id is the deduplication constraint the Kafka consumer relies
-- on (see docs/CONVENTIONS.md section 5: eventId is the idempotency key).

CREATE TABLE audit_events (
    version integer NOT NULL,
    entity_id bigint,
    "timestamp" timestamp(6) without time zone NOT NULL,
    event_id uuid NOT NULL,
    message character varying(1000),
    actor character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    service_name character varying(255) NOT NULL,
    payload jsonb,
    CONSTRAINT audit_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY[
        'BOOKING_CREATED'::character varying,
        'BOOKING_UPDATED'::character varying,
        'BOOKING_CANCELLED'::character varying,
        'USER_REGISTERED'::character varying,
        'USER_UPDATED'::character varying,
        'USER_DELETED'::character varying,
        'AI_REQUEST'::character varying,
        'AI_RESPONSE'::character varying,
        'AI_RATE_LIMITED'::character varying,
        'AI_ERROR'::character varying
    ])::text[]))),
    CONSTRAINT audit_events_pkey PRIMARY KEY (event_id)
);
