CREATE TABLE api_usage_counters
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    scope_type   VARCHAR(20)  NOT NULL,
    scope_id     VARCHAR(100) NOT NULL,
    feature      VARCHAR(50)  NOT NULL,
    window_type  VARCHAR(20)  NOT NULL,
    window_start DATETIME(6)  NOT NULL,
    used_count   BIGINT       NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_api_usage_counters PRIMARY KEY (id),
    CONSTRAINT uk_api_usage_counter_window
        UNIQUE (scope_type, scope_id, feature, window_type, window_start),
    CONSTRAINT ck_api_usage_counters_scope_type
        CHECK (scope_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_api_usage_counters_window_type
        CHECK (window_type IN ('MINUTE', 'DAY')),
    CONSTRAINT ck_api_usage_counters_used_count
        CHECK (used_count >= 0),
    CONSTRAINT ck_api_usage_counters_expiry
        CHECK (expires_at > window_start)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
