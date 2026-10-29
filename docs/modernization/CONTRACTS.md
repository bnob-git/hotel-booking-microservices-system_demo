# Booking & Loyalty Modernization — Shared Contracts

Phase 0 establishes the booking domain model and the event backbone. Phase 1 tracks (A–D) are built
in parallel on separate branches and reconciled in Phase 2, so **every cross-track interface is fixed
here**. A track may add to these contracts but must not change them without updating this file.

## Ports, databases, topics

| Component              | Port (container) | Host port | Database          |
|------------------------|------------------|-----------|-------------------|
| user-service           | 8081             | –         | `userservice_db`  |
| booking-service        | 8082             | –         | `booking_db`      |
| audit-service          | 8083             | –         | `audit_db`        |
| ai-chat-service        | 8084             | –         | –                 |
| loyalty-service (C)    | 8086             | –         | `loyalty_db`      |
| notification-service (D)| 8087            | –         | `notification_db` |
| mailpit (D)            | 1025 SMTP / 8025 UI | 8025   | –                 |
| api-gateway            | 8080             | 8080      | –                 |
| frontend (nginx)       | 80               | 3000      | –                 |

Kafka topics:
- `audit-events` — unchanged audit flow (`AuditEventRequest`). `BOOKING_CREATED` is still audited on create.
- `booking-events` — **new** domain events, key = `bookingId`, JSON **without** type headers.

## Booking domain (booking-service)

`Booking` columns (V2 migration): `status`, `total_price`, `num_guests`, `confirmation_code` (unique,
`HB` + 6 chars), `special_requests`, `contact_email`, `created_at`, `updated_at`.

`BookingStatus`: `PENDING → CONFIRMED → CHECKED_IN → COMPLETED`, plus terminal `CANCELLED` and
`EXPIRED`. Allowed transitions (Track B enforces):

| From        | To                                   |
|-------------|--------------------------------------|
| PENDING     | CONFIRMED, CANCELLED, EXPIRED        |
| CONFIRMED   | CHECKED_IN, CANCELLED                |
| CHECKED_IN  | COMPLETED                            |
| COMPLETED / CANCELLED / EXPIRED | – (terminal)     |

`CANCELLED` and `EXPIRED` bookings do **not** block a room in overlap/availability checks.
Phase 0 creates bookings directly as `CONFIRMED` (no payment step yet); Track B switches create to
`PENDING` + payment.

### Flyway migration version ranges (avoid collisions between parallel branches)

| Owner     | booking-service versions |
|-----------|--------------------------|
| Phase 0   | V1, V2 (`db/migration`), V3 (`db/seed`, demo data only) |
| Track A   | V10–V19                  |
| Track B   | V20–V29                  |
| Track D   | V30–V39                  |
| Phase 2   | V40+                     |

Tests run `db/migration` only (H2, PostgreSQL mode, `ddl-auto=validate`); demo data goes in `db/seed`.

## `booking-events` contract (schema v1)

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "eventType": "BOOKING_CREATED | BOOKING_CONFIRMED | BOOKING_CANCELLED | BOOKING_CHECKED_IN",
  "occurredAt": "2026-10-29T12:00:00Z",
  "bookingId": 42,
  "confirmationCode": "HB7K2QXM",
  "status": "CONFIRMED",
  "userId": 3,
  "username": "alice",
  "contactEmail": "alice@example.com",
  "roomId": 4,
  "roomName": "Room 201",
  "checkInDate": "2026-12-01",
  "checkOutDate": "2026-12-04",
  "numGuests": 2,
  "totalPrice": 360.00,
  "currency": "EUR"
}
```

- `userId` is `null` for guest bookings (Track D); `username` is the guest name in that case.
- Consumers deserialize into their **own** copy of this record (no shared jar), ignore unknown fields
  and event types, and use `ErrorHandlingDeserializer` so a bad record never blocks the partition.
- Consumers must be idempotent on `eventId` (at-least-once delivery).
- Consumer group ids: `loyalty-service`, `notification-service`.
- Events are published after the DB transaction commits (`BookingEventPublisher`).

Emitted by: create → `BOOKING_CREATED` (+ `BOOKING_CONFIRMED` once confirmed; Phase 0 confirms
immediately, Track B on successful payment); cancel → `BOOKING_CANCELLED`; check-in →
`BOOKING_CHECKED_IN`.

## Track A — Search & Pricing (booking-service, public endpoints)

- `GET /api/rooms/availability?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD[&guests=&type=&minPrice=&maxPrice=]`
  → `AvailableRoom[]`: `{ roomId, name, type, nightlyBaseRate, nights, quote: PriceQuote }`.
  Excludes rooms with `available=false` or an overlapping non-cancelled/non-expired booking.
- `POST /api/pricing/quote` `{ roomId, checkInDate, checkOutDate, promoCode? }` → `PriceQuote`:
  `{ roomId, nights, currency, baseAmount, seasonalAdjustment, promoCode, promoDiscount,
  promoApplied, promoMessage, totalPrice, nightly: [{ date, baseRate, multiplier, rate, season }] }`.
- Seasonal rules (table `seasonal_rates`, seeded): Peak `12-15 → 01-05` ×1.25, Summer `07-01 → 08-31` ×1.15,
  otherwise ×1.00. Applied per night.
- Promo codes (table `promo_codes`, seeded in a migration so tests/E2E can rely on them):
  `WELCOME10` 10% off; `FLAT25` €25 off (min spend €100); `SUMMER15` 15% off stays within Jul–Aug;
  `EXPIRED20` expired (must be rejected with a message). Promo discount is applied after seasonal
  adjustment and never makes the total negative.
- Price snapshot on booking: columns `base_amount`, `seasonal_adjustment`, `promo_code`,
  `promo_discount` (+ existing `total_price`), filled at create time from the quote; later rate/promo
  changes never alter existing bookings. `BookingRequest.promoCode` is optional.
- Gateway: `/api/pricing/**` and `GET /api/rooms/**` public.
- Frontend: `/search` page (date range, guests, type, price filters, promo code) → each result has a
  **Book** button navigating to `/checkout?roomId=&checkIn=&checkOut=&guests=&promo=`.

- Additive (Track A implementation, optional for consumers):
  - `AvailableRoom.maxGuests` (nullable integer) — room capacity used by the `guests` filter
    (column `rooms.max_guests`, V12; `NULL` = no limit).
  - `GET /api/rooms/availability` also accepts optional `promoCode`, so every result's `quote` already
    carries the promo outcome (`promoApplied`/`promoMessage`). Results are sorted by `quote.totalPrice` asc.
  - Invalid/expired/ineligible `promoCode` on `POST /api/bookings` → `400` with the same human message as the quote.
  - Promo validity (`validFrom`/`validTo`) is checked against today; the optional stay window
    (`MM-DD` start/end, may wrap the year) must contain every night of the stay. `minSpend` is checked
    against the seasonal subtotal. Currency is `EUR`.

## Track B — Payments & Lifecycle (booking-service)

- `POST /api/bookings` creates a `PENDING` hold with `hold_expires_at = now + booking.hold.ttl`
  (default 15m) and no `BOOKING_CONFIRMED` yet.
- `POST /api/bookings/{id}/pay` `{ paymentMethod: "tok_visa" | "tok_chargeDeclined" | ... }` — mock PSP
  (`PaymentGateway` interface, `MockPaymentGateway` default; Stripe optional behind config).
  `tok_chargeDeclined` → 402 and booking stays `PENDING`. Success → `CONFIRMED`, sets
  `payment_status=PAID`, `payment_reference`, `amount_paid`, emits `BOOKING_CONFIRMED`.
- `POST /api/bookings/{id}/cancel` `{ reason? }` — soft cancel (row kept, status `CANCELLED`,
  `cancelled_at`, `cancellation_reason`, `refund_amount`), emits `BOOKING_CANCELLED`.
  Policy (`CancellationPolicy`): ≥7 days before check-in → 100% refund; 2–6 days → 50%; <2 days → 0%;
  `PENDING` (unpaid) → no charge. Cannot cancel `CHECKED_IN`/`COMPLETED`.
  `DELETE /api/bookings/{id}` becomes an alias for soft cancel.
- `POST /api/bookings/{id}/check-in` (ADMIN) → `CHECKED_IN`, emits `BOOKING_CHECKED_IN`;
  `POST /api/bookings/{id}/complete` (ADMIN) → `COMPLETED`.
- `BookingStateMachine` validates transitions; illegal transition → 409.
- Hold expiry: `@Scheduled` job (`booking.hold.expiry-interval`, default 60s) moves `PENDING` bookings
  past `hold_expires_at` to `EXPIRED`.
- Frontend: `/checkout` page reads the query params above, collects guests/email/special requests,
  creates the booking, pays with a mock card selector, shows confirmation code + status.
  `/bookings` shows status badges, refund preview and soft-cancel.

## Track C — Loyalty (`loyalty-service`, JWT-protected unless noted)

- `GET /api/loyalty/me` → `{ userId, username, pointsBalance, lifetimePoints, tier, nextTier,
  pointsToNextTier, pointValue }`
- `GET /api/loyalty/me/transactions` → `[{ id, type: EARN|REDEEM|REVERSAL|ADJUST, points,
  bookingId, confirmationCode, description, createdAt }]`
- `GET /api/loyalty/tiers` (public) → `[{ tier, minLifetimePoints, earnMultiplier, perks[] }]`
- `POST /api/loyalty/redeem` `{ points, bookingReference? }` → `{ redemptionId, pointsRedeemed,
  discountAmount, pointsBalance }`
- Internal (no JWT, not routed by gateway): `POST /api/loyalty/internal/redeem`
  `{ userId, points, bookingReference }` and `POST /api/loyalty/internal/redemptions/{id}/reverse`
  — used by booking-service in Phase 2.
- Rules: earn 10 points per €1 of `totalPrice` × tier multiplier on `BOOKING_CONFIRMED`;
  reverse the earned points on `BOOKING_CANCELLED`; ignore events with `userId == null`.
  Tiers by lifetime points: BRONZE 0 (×1.0), SILVER 1,000 (×1.25), GOLD 5,000 (×1.5),
  PLATINUM 15,000 (×2.0). Redemption: multiples of 100 points, 100 points = €5
  (`pointValue = 0.05`), cannot exceed balance.
- Frontend: `/loyalty` dashboard (balance, tier progress, tiers table, transactions).

## Track D — Notifications & Guest UX

- `notification-service` consumes `booking-events` and sends HTML+text email via SMTP
  (Mailpit locally: `MAIL_HOST=mailpit`, `MAIL_PORT=1025`) for `BOOKING_CONFIRMED`,
  `BOOKING_CANCELLED`, `BOOKING_CHECKED_IN` to `contactEmail` (skip if absent). Logs each send in
  `notification_db` (idempotent on `eventId`). Subject format: `"[Hotel] Booking HB7K2QXM confirmed"`.
- Guest endpoints in booking-service (public, no JWT; gateway + service security permit
  `/api/bookings/guest/**`):
  - `POST /api/bookings/guest` `{ roomId, checkInDate, checkOutDate, numGuests, guestName,
    guestEmail, specialRequests? }` → `BookingResponse` (with `confirmationCode`)
  - `GET /api/bookings/guest/lookup?code=&email=` → `BookingResponse` (404 unless both match,
    email case-insensitive)
  - `POST /api/bookings/guest/cancel` `{ code, email }`
  - Migration: `user_id` nullable, new `guest_name` column.
- Frontend: `/guest/book` and `/guest/lookup` pages (no login required).

## Frontend conventions

- Routes: `/search` (A), `/checkout` (B), `/loyalty` (C), `/guest/book` + `/guest/lookup` (D).
- Each track adds its own key group to `core/config/api.config.ts` and its own service under
  `core/services/`, and one navbar link. Keep `data-testid` attributes on key controls for Playwright.
