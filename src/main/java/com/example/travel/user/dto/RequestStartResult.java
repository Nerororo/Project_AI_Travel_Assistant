package com.example.travel.user.dto;

public record RequestStartResult(RequestExecutionLease lease, Long retryAfterSeconds) {

	public RequestStartResult {
		if ((lease == null) == (retryAfterSeconds == null)) {
			throw new IllegalArgumentException("Exactly one start result value is required");
		}
		if (retryAfterSeconds != null && retryAfterSeconds < 1) {
			throw new IllegalArgumentException("retryAfterSeconds must be positive");
		}
	}

	public static RequestStartResult started(RequestExecutionLease lease) {
		return new RequestStartResult(lease, null);
	}

	public static RequestStartResult rateLimited(long retryAfterSeconds) {
		return new RequestStartResult(null, retryAfterSeconds);
	}

	public boolean started() {
		return lease != null;
	}
}
