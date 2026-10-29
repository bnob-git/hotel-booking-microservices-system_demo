-- Track A: room capacity for the availability search guests filter (NULL = no limit).
ALTER TABLE rooms ADD COLUMN max_guests INTEGER;

UPDATE rooms SET max_guests = 2 WHERE type = 'STANDARD';
UPDATE rooms SET max_guests = 3 WHERE type = 'DELUXE';
UPDATE rooms SET max_guests = 4 WHERE type = 'SUITE';
