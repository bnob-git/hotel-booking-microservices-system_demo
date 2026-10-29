# Before / after: booking & loyalty modernization

Baseline: `main` @ `cb7e004`. After: `devin/1793276400-phase2-integration` (Phase 0 + Tracks A–D + Phase 2).
Both recordings are Playwright built-in videos (1280×720) driven against `docker compose up --build`.

## User-visible flow

| Step | Before | After |
|---|---|---|
| Find a room | Static list of all rooms with a flat nightly price | `/search`: availability for a date range, guests / type / price filters, sorted by total |
| Price | `room.price` (shown per night; total not stored) | Seasonal rates (peak Dec 15–Jan 5 ×1.25, summer Jul–Aug ×1.15), promo codes (`WELCOME10`, `FLAT25`, `SUMMER15`), price snapshot on the booking (`baseAmount`, `seasonalAdjustment`, `promoDiscount`, `totalPrice`) |
| Book | Pick room → type dates → "Create Booking" (immediately final) | `/checkout`: review quote → guest details (guests, email, special requests, **redeem loyalty points**) → 15-minute `PENDING` hold → mock card payment → `CONFIRMED` with `confirmationCode` |
| Confirmation | "Booking created successfully" text | Confirmation page (code, status, promo and points discounts, amount paid, payment ref) + HTML email via notification-service (Mailpit) |
| Manage | `/bookings` list; Cancel = hard `DELETE` | Status + payment badges, cancellation quote, soft cancel with refund policy (100% ≥7 days, 50% 2–6 days, 0% <2 days), redeemed points returned; admin Check-in / Complete |
| Without an account | Not possible (all booking APIs need JWT) | `/guest/book` and `/guest/lookup` (confirmation code + email, rate-limited), guest cancel |
| Loyalty | None | `/loyalty`: balance, tier (Bronze/Silver/Gold/Platinum) + progress, perks, points history; 10 pts/€ × tier multiplier on `BOOKING_CONFIRMED`; 100 pts = €5 at checkout |

## Platform changes

- **booking-service**: `Booking` gains `status`, `totalPrice`, `numGuests`, `confirmationCode`, `specialRequests` (+ contact email, timestamps, payment, price snapshot, loyalty redemption); schema owned by Flyway (`V1`–`V40`) instead of `ddl-auto=create`; `BookingStateMachine`; `HoldExpiryJob`; `PaymentGateway` (mock, optional Stripe); Feign `LoyaltyClient`.
- **Kafka**: existing `audit-events` / `BOOKING_CREATED` audit flow unchanged; new versioned `booking-events` topic (`BOOKING_CREATED|CONFIRMED|CANCELLED|CHECKED_IN`) published after commit.
- **New services**: `loyalty-service` (8086, `loyalty_db`) and `notification-service` (8087, `notification_db`) + Mailpit (8025); registered in `api-gateway`, `docker-compose.yml`, `.env.example`, `init.sql`, CI.
- **Behaviour changes by design**: bookings are created `PENDING` until paid (previously final at creation); `DELETE /api/bookings/{id}` is now a soft cancel; cancelled / expired bookings no longer block availability.

## Verification

| Suite | Result |
|---|---|
| booking-service (JUnit, H2 + Flyway) | 227 passed |
| loyalty-service (incl. Embedded Kafka accrual) | 65 passed |
| notification-service (incl. Embedded Kafka + SMTP) | 28 passed |
| audit-service / user-service / api-gateway | 12 / 19 / 21 passed |
| frontend (Karma) | 81 passed, 79% line coverage |
| Playwright E2E (`frontend/e2e`) on a fresh `docker compose up --build` | 10/10 passed |
