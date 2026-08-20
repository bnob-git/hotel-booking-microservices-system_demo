CREATE TABLE rooms (
    id        BIGSERIAL      PRIMARY KEY,
    name      VARCHAR(255)   NOT NULL UNIQUE,
    type      VARCHAR(255),
    price     NUMERIC(38, 2),
    available BOOLEAN        NOT NULL
);

CREATE TABLE bookings (
    id             BIGSERIAL PRIMARY KEY,
    check_in_date  DATE      NOT NULL,
    check_out_date DATE      NOT NULL,
    room_id        BIGINT    NOT NULL,
    user_id        BIGINT    NOT NULL
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_room_id_dates ON bookings (room_id, check_in_date, check_out_date);
