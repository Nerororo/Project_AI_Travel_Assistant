package com.example.travel.user.dto;

import java.util.Objects;
import java.util.Set;

public record UsageReservationResult(
		boolean acquired,
		Long retryAfterSeconds,
		Set<UsageDenialScope> deniedScopes
) {

	public UsageReservationResult {
		Objects.requireNonNull(deniedScopes, "deniedScopes must not be null");
		deniedScopes = Set.copyOf(deniedScopes);
		if (acquired && retryAfterSeconds != null) {
			throw new IllegalArgumentException("An acquired reservation cannot have retryAfterSeconds");
		}
		if (acquired && !deniedScopes.isEmpty()) {
			throw new IllegalArgumentException("An acquired reservation cannot have deniedScopes");
		}
		if (!acquired && (retryAfterSeconds == null || retryAfterSeconds < 1)) {
			throw new IllegalArgumentException("A denied reservation requires positive retryAfterSeconds");
		}
		if (!acquired && deniedScopes.isEmpty()) {
			throw new IllegalArgumentException("A denied reservation requires deniedScopes");
		}
	}

	public static UsageReservationResult success() {
		return new UsageReservationResult(true, null, Set.of());
	}

	public static UsageReservationResult denied(
			long retryAfterSeconds,
			Set<UsageDenialScope> deniedScopes
	) {
		return new UsageReservationResult(false, retryAfterSeconds, deniedScopes);
	}
}
