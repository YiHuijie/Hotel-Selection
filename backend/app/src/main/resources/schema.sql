CREATE TABLE IF NOT EXISTS hotel (
    id BIGINT PRIMARY KEY,
    source_id VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(80) NOT NULL,
    province VARCHAR(80),
    district VARCHAR(80),
    address VARCHAR(500),
    rating DECIMAL(3, 1),
    image VARCHAR(1000),
    amenities VARCHAR(1000) NOT NULL DEFAULT '',
    keywords VARCHAR(1000)
);

ALTER TABLE hotel ADD COLUMN IF NOT EXISTS source_id VARCHAR(32);
ALTER TABLE hotel ADD COLUMN IF NOT EXISTS image VARCHAR(1000);
ALTER TABLE hotel ADD COLUMN IF NOT EXISTS keywords VARCHAR(1000);
ALTER TABLE hotel ADD COLUMN IF NOT EXISTS province VARCHAR(80);
ALTER TABLE hotel ADD COLUMN IF NOT EXISTS district VARCHAR(80);
ALTER TABLE hotel ALTER COLUMN rating DROP NOT NULL;

CREATE TABLE IF NOT EXISTS hotel_detail (
    hotel_id BIGINT PRIMARY KEY,
    longitude DECIMAL(13, 9),
    latitude DECIMAL(12, 9),
    opening_year INT,
    renovation_year INT,
    phone VARCHAR(80),
    gym BOOLEAN,
    laundry BOOLEAN,
    parking BOOLEAN,
    luggage_storage BOOLEAN,
    robot_service BOOLEAN,
    restaurant BOOLEAN,
    taxi_service BOOLEAN,
    meeting_room BOOLEAN,
    accessibility BOOLEAN,
    FOREIGN KEY (hotel_id) REFERENCES hotel(id)
);

ALTER TABLE hotel_detail ADD COLUMN IF NOT EXISTS province VARCHAR(80);
ALTER TABLE hotel_detail ADD COLUMN IF NOT EXISTS district VARCHAR(80);
UPDATE hotel SET
    province = COALESCE(province, (SELECT d.province FROM hotel_detail d WHERE d.hotel_id = hotel.id)),
    district = COALESCE(district, (SELECT d.district FROM hotel_detail d WHERE d.hotel_id = hotel.id));
ALTER TABLE hotel_detail DROP COLUMN IF EXISTS province;
ALTER TABLE hotel_detail DROP COLUMN IF EXISTS district;

CREATE TABLE IF NOT EXISTS room_offer (
    id BIGINT PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    room_name VARCHAR(255),
    price_per_night DECIMAL(10, 2),
    breakfast BOOLEAN,
    available_from DATE,
    available_to DATE,
    available_on DATE NOT NULL,
    hair_dryer BOOLEAN,
    towels BOOLEAN,
    inventory_source_hotel_id VARCHAR(32),
    FOREIGN KEY (hotel_id) REFERENCES hotel(id)
);

ALTER TABLE room_offer ADD COLUMN IF NOT EXISTS available_on DATE;
ALTER TABLE room_offer ADD COLUMN IF NOT EXISTS hair_dryer BOOLEAN;
ALTER TABLE room_offer ADD COLUMN IF NOT EXISTS towels BOOLEAN;
ALTER TABLE room_offer ADD COLUMN IF NOT EXISTS inventory_source_hotel_id VARCHAR(32);
ALTER TABLE room_offer ALTER COLUMN price_per_night DROP NOT NULL;
ALTER TABLE room_offer ALTER COLUMN breakfast DROP NOT NULL;
UPDATE room_offer SET available_on = available_from WHERE available_on IS NULL;

CREATE TABLE IF NOT EXISTS review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    source_review_id VARCHAR(32),
    user_id VARCHAR(120),
    user_name VARCHAR(120),
    rating INT,
    content VARCHAR(4000),
    created_at TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotel(id)
);

ALTER TABLE review ADD COLUMN IF NOT EXISTS source_review_id VARCHAR(32);
ALTER TABLE review ADD COLUMN IF NOT EXISTS user_name VARCHAR(120);
ALTER TABLE review ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE review ALTER COLUMN rating DROP NOT NULL;
ALTER TABLE review ALTER COLUMN content DROP NOT NULL;
ALTER TABLE review ALTER COLUMN created_at DROP NOT NULL;

CREATE TABLE IF NOT EXISTS agent_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(120) NOT NULL,
    title VARCHAR(120),
    state VARCHAR(40) NOT NULL,
    pending_relaxation VARCHAR(40),
    requirements_json TEXT NOT NULL,
    latest_reply_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE agent_session ADD COLUMN IF NOT EXISTS workflow_version INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS agent_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS session_evaluation (
    session_id VARCHAR(36) PRIMARY KEY,
    request_count BIGINT NOT NULL DEFAULT 0,
    error_count BIGINT NOT NULL DEFAULT 0,
    duration_millis BIGINT,
    model_calls INT,
    hard_satisfied INT,
    hard_total INT,
    preference_satisfied INT,
    preference_total INT,
    preference_unknown INT,
    history_complete BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE
);

ALTER TABLE session_evaluation ADD COLUMN IF NOT EXISTS history_complete BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS user_preference_memory (
    user_id VARCHAR(120) PRIMARY KEY,
    preferences_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_preference_event (
    user_id VARCHAR(120) NOT NULL,
    source_type VARCHAR(10) NOT NULL,
    source_id BIGINT NOT NULL,
    occurred_at TIMESTAMP,
    preferences_json TEXT NOT NULL,
    PRIMARY KEY (user_id, source_type, source_id),
    FOREIGN KEY (user_id) REFERENCES user_preference_memory(user_id)
);
