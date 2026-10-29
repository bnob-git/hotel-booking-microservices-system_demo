-- Demo data (only loaded via the db/seed location; tests use db/migration only).
INSERT INTO rooms (name, type, price, available)
VALUES ('Room 101', 'STANDARD', 60.00, true),
       ('Room 102', 'STANDARD', 65.00, true),
       ('Room 103', 'STANDARD', 70.00, true),
       ('Room 201', 'DELUXE', 120.00, true),
       ('Room 202', 'DELUXE', 130.00, true),
       ('Room 203', 'DELUXE', 140.00, true),
       ('Suite 301', 'SUITE', 200.00, true),
       ('Suite 302', 'SUITE', 220.00, true),
       ('Penthouse', 'SUITE', 350.00, false),
       ('Budget Room', 'STANDARD', 50.00, true);

INSERT INTO bookings (check_in_date, check_out_date, user_id, room_id, status, total_price, num_guests, confirmation_code)
VALUES ('2026-12-10', '2026-12-12', 1, 1, 'CONFIRMED', 120.00, 1, 'HBSEED01'),
       ('2026-12-15', '2026-12-18', 2, 2, 'CONFIRMED', 195.00, 2, 'HBSEED02'),
       ('2026-12-20', '2026-12-22', 3, 3, 'CONFIRMED', 140.00, 1, 'HBSEED03'),
       ('2026-12-28', '2027-01-02', 4, 7, 'CONFIRMED', 1000.00, 2, 'HBSEED04'),
       ('2027-01-05', '2027-01-08', 5, 8, 'CONFIRMED', 660.00, 3, 'HBSEED05'),
       ('2027-01-15', '2027-01-18', 1, 4, 'CONFIRMED', 360.00, 2, 'HBSEED06');
