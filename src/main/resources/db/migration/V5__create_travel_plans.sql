CREATE TABLE travel_plans
(
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    region_id VARCHAR(100) NOT NULL,
    region_display_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    travel_mode VARCHAR(20) NOT NULL,
    meal_travel_buffer_minutes INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_travel_plans PRIMARY KEY (id),
    CONSTRAINT fk_travel_plans_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_travel_plans_dates CHECK (start_date <= end_date),
    CONSTRAINT ck_travel_plans_mode CHECK (travel_mode IN ('CAR', 'PUBLIC_TRANSIT')),
    CONSTRAINT ck_travel_plans_meal_buffer CHECK (meal_travel_buffer_minutes BETWEEN 0 AND 60),
    INDEX ix_travel_plans_user_start_date (user_id, start_date)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE food_preferences
(
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_plan_id BIGINT NOT NULL,
    food_name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_food_preferences PRIMARY KEY (id),
    CONSTRAINT uk_food_preferences_plan_name UNIQUE (travel_plan_id, food_name),
    CONSTRAINT fk_food_preferences_plan FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE travel_plan_shares
(
    travel_plan_id BIGINT NOT NULL,
    token_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_travel_plan_shares PRIMARY KEY (travel_plan_id),
    CONSTRAINT uk_travel_plan_shares_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_travel_plan_shares_plan FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id),
    CONSTRAINT ck_travel_plan_shares_expiry CHECK (expires_at > created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plan_places
(
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_plan_id BIGINT NOT NULL,
    kakao_place_id VARCHAR(255) NOT NULL,
    place_url VARCHAR(1000) NOT NULL,
    role VARCHAR(20) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    memo VARCHAR(1000) NULL,
    stay_minutes INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_plan_places PRIMARY KEY (id),
    CONSTRAINT uk_plan_places_id_plan UNIQUE (id, travel_plan_id),
    CONSTRAINT fk_plan_places_plan FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id),
    CONSTRAINT ck_plan_places_role CHECK (role IN ('ATTRACTION', 'HOTEL', 'RESTAURANT')),
    CONSTRAINT ck_plan_places_stay CHECK (
        (role = 'ATTRACTION' AND stay_minutes IS NOT NULL
            AND stay_minutes BETWEEN 30 AND 480 AND MOD(stay_minutes, 10) = 0)
        OR (role <> 'ATTRACTION' AND stay_minutes IS NULL)
    ),
    INDEX ix_plan_places_plan_role (travel_plan_id, role)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE travel_plan_days
(
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_plan_id BIGINT NOT NULL,
    day_number INT NOT NULL,
    travel_date DATE NOT NULL,
    activity_start_time TIME NOT NULL,
    activity_end_time TIME NOT NULL,
    CONSTRAINT pk_travel_plan_days PRIMARY KEY (id),
    CONSTRAINT uk_travel_plan_days_plan_number UNIQUE (travel_plan_id, day_number),
    CONSTRAINT uk_travel_plan_days_plan_date UNIQUE (travel_plan_id, travel_date),
    CONSTRAINT uk_travel_plan_days_id_plan UNIQUE (id, travel_plan_id),
    CONSTRAINT fk_travel_plan_days_plan FOREIGN KEY (travel_plan_id) REFERENCES travel_plans (id),
    CONSTRAINT ck_travel_plan_days_number CHECK (day_number BETWEEN 1 AND 7),
    CONSTRAINT ck_travel_plan_days_times CHECK (activity_start_time < activity_end_time)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE travel_plan_items
(
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_plan_day_id BIGINT NOT NULL,
    travel_plan_id BIGINT NOT NULL,
    item_order INT NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    plan_place_id BIGINT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    estimated_minutes INT NULL,
    CONSTRAINT pk_travel_plan_items PRIMARY KEY (id),
    CONSTRAINT uk_travel_plan_items_day_order UNIQUE (travel_plan_day_id, item_order),
    CONSTRAINT fk_travel_plan_items_day_plan FOREIGN KEY (travel_plan_day_id, travel_plan_id)
        REFERENCES travel_plan_days (id, travel_plan_id),
    CONSTRAINT fk_travel_plan_items_place_plan FOREIGN KEY (plan_place_id, travel_plan_id)
        REFERENCES plan_places (id, travel_plan_id),
    CONSTRAINT ck_travel_plan_items_order CHECK (item_order >= 1),
    CONSTRAINT ck_travel_plan_items_times CHECK (start_time < end_time),
    CONSTRAINT ck_travel_plan_items_shape CHECK (
        (item_type = 'MOVE' AND plan_place_id IS NULL AND estimated_minutes IS NOT NULL AND estimated_minutes > 0
            AND MOD(estimated_minutes, 10) = 0)
        OR (item_type IN ('VISIT', 'STAY') AND plan_place_id IS NOT NULL AND estimated_minutes IS NULL)
        OR (item_type = 'MEAL' AND estimated_minutes IS NULL)
    ),
    INDEX ix_travel_plan_items_day_plan (travel_plan_day_id, travel_plan_id),
    INDEX ix_travel_plan_items_place_plan (plan_place_id, travel_plan_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
