ALTER TABLE api_usage_counters
    DROP CHECK ck_api_usage_counters_feature;

ALTER TABLE api_usage_counters
    ADD CONSTRAINT ck_api_usage_counters_feature
        CHECK (feature IN (
            'PLACE_SEARCH', 'CAR_ROUTE', 'PUBLIC_TRANSIT_ROUTE',
            'AI_REGION_RECOMMENDATION', 'AI_MENU_ANALYSIS',
            'TRAVEL_PLAN_ESTIMATE', 'TRAVEL_PLAN_CREATE'
        ));

CREATE TABLE travel_plan_creation_locks
(
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_travel_plan_creation_locks PRIMARY KEY (user_id),
    CONSTRAINT fk_travel_plan_creation_locks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_travel_plans_user_created_id ON travel_plans (user_id, created_at DESC, id DESC);
