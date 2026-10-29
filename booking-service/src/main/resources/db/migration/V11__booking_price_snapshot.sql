-- Track A: price snapshot taken at booking time; later rate/promo changes never alter existing bookings.
ALTER TABLE bookings ADD COLUMN base_amount NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN seasonal_adjustment NUMERIC(12, 2);
ALTER TABLE bookings ADD COLUMN promo_code VARCHAR(32);
ALTER TABLE bookings ADD COLUMN promo_discount NUMERIC(12, 2);
