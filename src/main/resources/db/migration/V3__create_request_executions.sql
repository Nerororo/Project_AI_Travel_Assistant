CREATE TABLE request_executions
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    feature    VARCHAR(50) NOT NULL,
    request_id CHAR(36)    NOT NULL,
    status     VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_request_executions PRIMARY KEY (id),
    CONSTRAINT uk_request_execution_key UNIQUE (user_id, feature, request_id),
    CONSTRAINT fk_request_executions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_request_executions_status
        CHECK (status IN ('PROCESSING', 'SUCCESS')),
    INDEX ix_request_executions_expires_at (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
