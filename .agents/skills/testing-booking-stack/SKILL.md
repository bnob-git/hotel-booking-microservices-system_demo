---
name: testing-booking-stack
description: How to bring up and end-to-end test the hotel booking microservices stack (Angular frontend + Spring Boot services + Kafka) locally with Docker Compose.
---

# Testing the hotel booking stack

## Bring the stack up
```bash
cp -n .env.${Example_Secret} .env          # placeholders are fine for local/dev
docker compose up -d --build     # ~5-10 min on a cold build
docker compose ps                # all 8 containers should be Up
```
Services: frontend http://localhost:3000, api-gateway http://localhost:8080,
kafka-ui http://localhost:8085. user/booking/audit services (8081/8082/8083)
are **not** published to the host — curl them from inside the network:
```bash
docker compose exec -T api-gateway sh -c "wget -qO- http://user-service:8081/actuator/health"
```
To re-seed the demo data, `docker compose down -v` first (ddl-auto: create +
data.sql only run on a fresh DB volume).

## Credentials
- Admin: `admin` / `admin123` (created by `AuthService.initAdminUser()` @PostConstruct).
- Seeded users (`user-service/src/main/resources/data.sql`): demo, alice, bob,
  charlie, diana — passwords are bcrypt hashes; see README "Sample Users".

## UI paths (Angular routes in `frontend/src/app/app.routes.ts`)
- `/` rooms grid → "Book" button → `/bookings?roomId=N`
- `/bookings` create booking (date inputs are native `type=date`, type `MM/DD/YYYY`) + list with "Cancel"
- `/admin` (admin only) Users + Rooms tables, "Add User" / "Add Room" dialogs
- `/chat` (admin only) requires ai-chat-service with `gemini`/`ollama` compose profile + GEMINI_API_KEY
- There is **no self-registration UI and no audit-log UI**. Create users via
  Admin → Add User. Verify audit events in Kafka UI
  (http://localhost:8085/ui/clusters/local/all-topics/audit-events/messages)
  and/or `docker compose exec -T postgres psql -U postgres -d audit_db -c "select event_type,actor,message,timestamp from audit_events order by timestamp desc limit 5;"`
  (table has no `id`/`created_at` columns — use `event_id`/`timestamp`).

## Gotcha: Spring profiles vs CORS (browser 403 on every API call)
`api-gateway/.../config/CorsConfig.java` is annotated `@Profile("dev")` and only
allows origin `http://localhost:4200` (the `ng serve` origin), **not** the
Dockerized frontend at `http://localhost:3000`. If the `dev` profile is active on
the gateway (e.g. via `spring.profiles.default: dev` or
`SPRING_PROFILES_ACTIVE=dev`), the browser gets `403` on `POST /api/auth/login`
and the UI shows "Invalid credentials or user does not exist", even though the
same request from curl (no `Origin` header) returns 200.

Diagnose:
```bash
docker compose logs frontend | tail        # nginx access log shows 403 for browser, 200 for curl
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' -H 'Origin: http://localhost:3000' \
  -d '{"username":"admin","password":"admin123"}'
```
Workaround for testing (does not fix the product): run the gateway with
`SPRING_PROFILES_ACTIVE=default` via a compose override file. The real fix is to
add `http://localhost:3000` to the allowed origins (or make them configurable).

## Devin Secrets Needed
- None for the core stack. `GEMINI_API_KEY` is only needed for the optional
  ai-chat-service (`docker compose --profile gemini up -d`).
