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

import java.time.Instant;

@Entity
@Table(
		name = "api_usage_counters",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_api_usage_counter_window",
				columnNames = {"scope_type", "scope_id", "feature", "window_type", "window_start"}
		)
)
public class ApiUsageCounter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope_type", nullable = false, length = 20)
	private UsageScopeType scopeType;

	@Column(name = "scope_id", nullable = false, length = 100)
	private String scopeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private UsageFeature feature;

	@Enumerated(EnumType.STRING)
	@Column(name = "window_type", nullable = false, length = 20)
	private UsageWindowType windowType;

	@Column(name = "window_start", nullable = false)
	private Instant windowStart;

	@Column(name = "used_count", nullable = false)
	private long usedCount;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected ApiUsageCounter() {
	}

	public Long id() {
		return id;
	}

	public UsageScopeType scopeType() {
		return scopeType;
	}

	public String scopeId() {
		return scopeId;
	}

	public UsageFeature feature() {
		return feature;
	}

	public UsageWindowType windowType() {
		return windowType;
	}

	public Instant windowStart() {
		return windowStart;
	}

	public long usedCount() {
		return usedCount;
	}

	public Instant expiresAt() {
		return expiresAt;
	}
}
