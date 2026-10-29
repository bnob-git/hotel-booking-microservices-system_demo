-- Track B: payment step, soft cancel and lifecycle timestamps.
ALTER TABLE bookings ADD COLUMN hold_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bookings ADD COLUMN payment_status VARCHAR(20) DEFAULT 'UNPAID' NOT NULL;
ALTER TABLE bookings ADD COLUMN payment_reference VARCHAR(100);
ALTER TABLE bookings ADD COLUMN amount_paid NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN refund_amount NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bookings ADD COLUMN cancellation_reason VARCHAR(500);
ALTER TABLE bookings ADD COLUMN checked_in_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bookings ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

-- Bookings confirmed before the payment step existed are treated as settled for their total price.
UPDATE bookings SET payment_status = 'PAID', amount_paid = total_price
WHERE status IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED');

CREATE INDEX idx_bookings_status_hold ON bookings (status, hold_expires_at);
