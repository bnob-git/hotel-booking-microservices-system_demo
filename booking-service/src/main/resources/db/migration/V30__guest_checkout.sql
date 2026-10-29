-- Track D: guest checkout. Guest bookings have no user account, only a name and contact email.
ALTER TABLE bookings ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE bookings ADD COLUMN guest_name VARCHAR(255);
