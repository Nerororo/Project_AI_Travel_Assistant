package com.example.travel.user.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RequestExecutionLease(
		long userId,
		UsageFeature feature,
		UUID requestId,
		Instant expiresAt
) {
	public RequestExecutionLease {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be positive");
		}
		Objects.requireNonNull(feature);
		Objects.requireNonNull(requestId);
		Objects.requireNonNull(expiresAt);
	}
}
