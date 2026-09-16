package com.example.travel.user.dto;

public record UsageReservationResult(boolean acquired, Long retryAfterSeconds) {

	public UsageReservationResult {
		if (acquired && retryAfterSeconds != null) {
			throw new IllegalArgumentException("An acquired reservation cannot have retryAfterSeconds");
		}
		if (!acquired && (retryAfterSeconds == null || retryAfterSeconds < 1)) {
			throw new IllegalArgumentException("A denied reservation requires positive retryAfterSeconds");
		}
	}

	public static UsageReservationResult success() {
		return new UsageReservationResult(true, null);
	}

	public static UsageReservationResult denied(long retryAfterSeconds) {
		return new UsageReservationResult(false, retryAfterSeconds);
	}
}
