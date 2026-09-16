package com.example.travel.user.domain;

import com.example.travel.user.dto.UsageFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "request_executions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_request_execution_key",
				columnNames = {"user_id", "feature", "request_id"}
		)
)
public class RequestExecution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private UsageFeature feature;

	@Column(name = "request_id", nullable = false, length = 36)
	@JdbcTypeCode(SqlTypes.CHAR)
	private UUID requestId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestExecutionStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected RequestExecution() {
	}

	public Long id() {
		return id;
	}

	public Long userId() {
		return userId;
	}

	public UsageFeature feature() {
		return feature;
	}

	public UUID requestId() {
		return requestId;
	}

	public RequestExecutionStatus status() {
		return status;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public void restart(Instant newExpiresAt) {
		status = RequestExecutionStatus.PROCESSING;
		expiresAt = newExpiresAt;
	}
}
