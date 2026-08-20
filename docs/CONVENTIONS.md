# Shared Conventions and Cross-Service Contracts

This document is the source of truth for conventions that span more than one
service. Per-service work (migrations, Feign interceptors, outbox/retry) must
follow what is written here so the services do not diverge.

Services covered: `user-service`, `booking-service`, `api-gateway`,
`audit-service`, `ai-chat-service`.

---

## 1. Spring profiles

Every service defines two environment profiles in
`src/main/resources/application.yaml` as additional YAML documents activated
with `spring.config.activate.on-profile`:

| Profile | Purpose |
| ------- | ------- |
| `dev`   | Local/demo behavior. Active by default (`spring.profiles.default: dev`). |
| `prod`  | Deployed behavior. Activated with `SPRING_PROFILES_ACTIVE=prod`. |

Environment-sensitive settings live in the profile documents, not in the base
document:

| Setting | `dev` | `prod` (intended) |
| ------- | ----- | ----------------- |
| `spring.jpa.hibernate.ddl-auto` | `create` | `validate` once Flyway migrations exist (currently `${JPA_DDL_AUTO:none}`) |
| `spring.jpa.show-sql` | `true` | `false` |
| `spring.sql.init.mode` | `always` | `never` |
| `spring.kafka.security.protocol` | `PLAINTEXT` | `${KAFKA_SECURITY_PROTOCOL:SASL_SSL}` |
| Kafka SASL | not used | `KAFKA_SASL_MECHANISM` (default `SCRAM-SHA-512`) and `KAFKA_SASL_JAAS_CONFIG` (no default) |
| `management.endpoint.health.show-details` | `always` | `never` |
| `app.cors.allowed-origins` (api-gateway) | `http://localhost:4200,http://localhost:3000` | `${CORS_ALLOWED_ORIGINS}` (no default) |

Notes:

- `ddl-auto` is deliberately **not** yet `validate`: flipping it is per-service
  work that lands together with that service's first Flyway migration.
- The AI provider profiles (`gemini`, `ollama`) of `ai-chat-service` are
  orthogonal to `dev`/`prod` and are combined, e.g.
  `SPRING_PROFILES_ACTIVE=prod,gemini`.
- Tests keep their own `test` profile (`src/test/resources/application-test.yml`,
  H2 in memory) and are unaffected by `dev`/`prod`.

## 2. Actuator

All five services depend on `spring-boot-starter-actuator` and expose a minimal
endpoint set:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`/actuator/health/**` is permitted without authentication in the services that
run Spring Security (`user-service`, `booking-service`, `api-gateway`) so it can
be used as a container/Kubernetes probe.

## 3. Flyway convention (no migrations yet)

Per-service sessions will introduce Flyway in the JPA services
(`user-service`, `booking-service`, `audit-service`). When they do:

- Migrations live in `src/main/resources/db/migration`.
- Files are named `V<n>__<description>.sql`, e.g. `V1__create_users_table.sql`.
  Version numbers are per service and never reused or edited once merged;
  corrections are made with a new version.
- The first migration must reproduce the schema Hibernate currently generates
  plus the data currently loaded through `data.sql`/`init.sql`, so switching is
  behavior-preserving.
- In `prod`, `spring.jpa.hibernate.ddl-auto: validate` and
  `spring.sql.init.mode: never` — Flyway owns the schema, Hibernate only
  validates it.
- In `dev` the demo behavior (`create` + `always`) may stay until a service has
  migrated; after migrating, `dev` should also run Flyway.
- Tests keep the H2 `test` profile. If a migration uses PostgreSQL-specific
  SQL, keep the test profile on Hibernate-generated schema (`create-drop`)
  rather than running Flyway against H2.

## 4. Internal service-to-service auth contract

Internal Feign calls (for example `bookingClient.hasBookings(id)` in
`user-service/src/main/java/com/hotel/userservice/service/UserService.java`,
which hits `/api/bookings/internal/**`) are currently unauthenticated. The
agreed mechanism is a shared internal service token:

- **Header:** `X-Internal-Token`
- **Env var:** `INTERNAL_SERVICE_TOKEN` (no baked-in default; services must fail
  fast if it is missing when the check is enabled)
- **Config key:** `internal.service-token: ${INTERNAL_SERVICE_TOKEN}`
- **Senders** — attach the header on every Feign call to an internal endpoint
  via a `RequestInterceptor`:

  | Sender | Calls |
  | ------ | ----- |
  | `user-service` | `booking-service` `/api/bookings/internal/**` |
  | `booking-service` | `user-service` `/api/users/internal/**` |
  | `ai-chat-service` | `user-service`, `booking-service` and `audit-service` internal endpoints |

- **Receivers** — validate the header on their internal endpoints and reject
  with `401` when it is absent or does not match:
  `user-service` (`/api/users/internal/**`),
  `booking-service` (`/api/bookings/internal/**`),
  `audit-service` (`/api/audit/internal/**`).

Because every internal endpoint currently has `permitAll()`, sender and
receiver changes must land together in one change; a receiver that starts
enforcing before its senders send the header breaks the stack.

Rules:

- The token is a shared secret across services; it is provided per environment
  (`.env` for Docker Compose, a Secret for Kubernetes) and never committed.
- Comparison must be constant-time.
- It is distinct from `AI_SERVICE_TOKEN`, which stays scoped to the AI chat
  flow between the gateway and `ai-chat-service`.
- The interceptor and validation logic are per-service work; this session only
  fixes the contract and adds the env placeholder.

## 5. Audit event schema contract

There is no shared module, so `AuditEventRequest` and `AuditEventType` are
duplicated in the three producers (`user-service`, `booking-service`,
`ai-chat-service`) and the consumer (`audit-service`). The canonical schema
below must stay identical in all four services.

Kafka topic: `audit-events` (JSON, no type headers; `audit-service` deserializes
into `com.hotel.audit.dto.AuditEventRequest`).

| Field | Type | Meaning |
| ----- | ---- | ------- |
| `eventId` | `UUID` | Unique event id, generated by the producer; used for idempotency/deduplication |
| `eventType` | `AuditEventType` | Serialized as its enum name |
| `serviceName` | `String` | Producing service, e.g. `user-service` |
| `actor` | `String` | Who triggered the action (username, or `system`) |
| `entityType` | `String` | Affected entity type, e.g. `USER`, `BOOKING` |
| `entityId` | `Long` | Affected entity id, nullable |
| `payload` | `Map<String, Object>` | Free-form details |
| `message` | `String` | Human-readable summary |

Event types (the consumer enum `com.hotel.audit.entity.AuditEventType` is the
union; each producer declares only the subset it emits):

| Producer | Event types |
| -------- | ----------- |
| `user-service` | `USER_REGISTERED`, `USER_UPDATED`, `USER_DELETED` |
| `booking-service` | `BOOKING_CREATED`, `BOOKING_UPDATED`, `BOOKING_CANCELLED` |
| `ai-chat-service` | `AI_REQUEST`, `AI_RESPONSE`, `AI_RATE_LIMITED`, `AI_ERROR` |

Rules:

- Any field addition, rename, or removal must be mirrored in all four services
  in the same change; the consumer must tolerate unknown fields.
- New event types are added to the consumer enum first, then to the producer
  that emits them, otherwise deserialization on the consumer fails.
- Later reliability work (transactional outbox, retries, DLQ) must keep this
  payload shape and must keep `eventId` stable across retries so the consumer
  can deduplicate.
