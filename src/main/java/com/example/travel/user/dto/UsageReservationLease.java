package com.example.travel.user.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Request-scoped handle that keeps a successful reservation tied to its original counter windows.
 */
public record UsageReservationLease(
		UsageReservationResult result,
		Instant windowReference
) {

	public UsageReservationLease {
		Objects.requireNonNull(result, "result must not be null");
		if (result.acquired() && windowReference == null) {
			throw new IllegalArgumentException("An acquired reservation requires a windowReference");
		}
		if (!result.acquired() && windowReference != null) {
			throw new IllegalArgumentException("A denied reservation cannot have a windowReference");
		}
	}

	public static UsageReservationLease acquired(Instant windowReference) {
		return new UsageReservationLease(
				UsageReservationResult.success(),
				Objects.requireNonNull(windowReference, "windowReference must not be null")
		);
	}

	public static UsageReservationLease denied(UsageReservationResult result) {
		if (result.acquired()) {
			throw new IllegalArgumentException("A denied lease requires a denied result");
		}
		return new UsageReservationLease(result, null);
	}
}
