CREATE TABLE auth_attempt_counters
(
    bucket_type     VARCHAR(32) NOT NULL,
    identifier_hash CHAR(64)    NOT NULL,
    window_start    DATETIME(6) NOT NULL,
    used_count      INT         NOT NULL,
    expires_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_auth_attempt_counters PRIMARY KEY (bucket_type, identifier_hash, window_start),
    CONSTRAINT ck_auth_attempt_counters_used_count CHECK (used_count >= 0),
    CONSTRAINT ck_auth_attempt_counters_expiry CHECK (expires_at > window_start),
    INDEX ix_auth_attempt_counters_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
