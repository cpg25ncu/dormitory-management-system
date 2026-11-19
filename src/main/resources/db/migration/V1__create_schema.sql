CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(40) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'STAFF')),
    credential_version INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE building (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(240) NOT NULL,
    manager_id BIGINT,
    FOREIGN KEY (manager_id) REFERENCES app_user(id)
);

CREATE TABLE room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL,
    room_number VARCHAR(30) NOT NULL,
    capacity INT NOT NULL CHECK (capacity BETWEEN 1 AND 30),
    UNIQUE (building_id, room_number),
    FOREIGN KEY (building_id) REFERENCES building(id)
);

CREATE TABLE student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_number VARCHAR(40) NOT NULL UNIQUE,
    full_name VARCHAR(80) NOT NULL,
    program VARCHAR(100) NOT NULL
);

CREATE TABLE stay (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    active_student_id BIGINT UNIQUE,
    check_in_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    check_in_by BIGINT NOT NULL,
    check_out_at TIMESTAMP(6),
    check_out_by BIGINT,
    departure_type VARCHAR(12) CHECK (departure_type IN ('TRANSFER', 'CHECKOUT')),
    departure_reason VARCHAR(240),
    CHECK (
        (check_out_at IS NULL AND active_student_id IS NOT NULL AND active_student_id = student_id
            AND check_out_by IS NULL AND departure_type IS NULL AND departure_reason IS NULL)
        OR (check_out_at IS NOT NULL AND active_student_id IS NULL
            AND check_out_by IS NOT NULL AND departure_type IS NOT NULL AND departure_reason IS NOT NULL)
    ),
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (room_id) REFERENCES room(id),
    FOREIGN KEY (check_in_by) REFERENCES app_user(id),
    FOREIGN KEY (check_out_by) REFERENCES app_user(id)
);

CREATE INDEX stay_room_occupancy ON stay (room_id, check_out_at);
CREATE INDEX stay_student_history ON stay (student_id, check_in_at);

CREATE TABLE absence_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stay_id BIGINT NOT NULL,
    absence_date DATE NOT NULL,
    reason VARCHAR(240) NOT NULL,
    recorded_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (stay_id, absence_date),
    FOREIGN KEY (stay_id) REFERENCES stay(id),
    FOREIGN KEY (recorded_by) REFERENCES app_user(id)
);
