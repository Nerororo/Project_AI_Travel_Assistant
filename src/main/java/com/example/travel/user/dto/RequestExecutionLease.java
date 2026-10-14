package com.example.travel.user.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

public final class RequestExecutionLease {
	private final long userId;
	private final UsageFeature feature;
	private final UUID requestId;
	private Instant expiresAt;
	private ScheduledFuture<?> renewal;

	public RequestExecutionLease(long userId, UsageFeature feature, UUID requestId, Instant expiresAt) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be positive");
		}
		this.userId = userId;
		this.feature = Objects.requireNonNull(feature);
		this.requestId = Objects.requireNonNull(requestId);
		this.expiresAt = Objects.requireNonNull(expiresAt);
	}

	public long userId() { return userId; }
	public UsageFeature feature() { return feature; }
	public UUID requestId() { return requestId; }
	public synchronized Instant expiresAt() { return expiresAt; }

	public synchronized void renewTo(Instant expiresAt) {
		this.expiresAt = Objects.requireNonNull(expiresAt);
	}

	public synchronized void scheduleRenewal(ScheduledFuture<?> renewal) {
		this.renewal = Objects.requireNonNull(renewal);
	}

	public synchronized void stopRenewal() {
		if (renewal != null) {
			renewal.cancel(false);
			renewal = null;
		}
	}
}
