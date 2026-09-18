package com.example.travel.route.client;

/**
 * Provider-independent failures. A normal missing route is {@link RouteResult.NotFound}, not a failure.
 */
public enum RouteClientFailure {
	INVALID_REQUEST(false),
	AUTHENTICATION_FAILED(false),
	ACCESS_DENIED(false),
	RATE_LIMITED(false),
	TIMEOUT(true),
	CONNECTION_FAILED(true),
	PROVIDER_UNAVAILABLE(true),
	INVALID_RESPONSE(false);

	private final boolean transientTechnicalFailure;

	RouteClientFailure(boolean transientTechnicalFailure) {
		this.transientTechnicalFailure = transientTechnicalFailure;
	}

	public boolean isTransientTechnicalFailure() {
		return transientTechnicalFailure;
	}
}
