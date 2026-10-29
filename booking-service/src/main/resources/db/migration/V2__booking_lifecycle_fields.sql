-- Phase 0: booking lifecycle fields shared by search/pricing, payments, loyalty and notifications.
ALTER TABLE bookings ADD COLUMN status VARCHAR(20) DEFAULT 'CONFIRMED' NOT NULL;
ALTER TABLE bookings ADD COLUMN total_price NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN num_guests INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE bookings ADD COLUMN confirmation_code VARCHAR(16);
ALTER TABLE bookings ADD COLUMN special_requests VARCHAR(1000);
ALTER TABLE bookings ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE bookings ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE bookings ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;

-- Backfill codes for pre-existing rows so the unique constraint can be enforced.
UPDATE bookings SET confirmation_code = CONCAT('HB', LPAD(CAST(id AS VARCHAR), 6, '0'))
WHERE confirmation_code IS NULL;

ALTER TABLE bookings ALTER COLUMN confirmation_code SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT uk_bookings_confirmation_code UNIQUE (confirmation_code);
CREATE INDEX idx_bookings_status ON bookings (status);
