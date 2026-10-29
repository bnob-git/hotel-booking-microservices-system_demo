-- Phase 2: loyalty points redeemed against the booking total (reversed on cancel / hold expiry).
ALTER TABLE bookings ADD COLUMN loyalty_points_redeemed BIGINT;
ALTER TABLE bookings ADD COLUMN loyalty_discount NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN loyalty_redemption_id BIGINT;
