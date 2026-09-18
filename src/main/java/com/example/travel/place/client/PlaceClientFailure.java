package com.example.travel.place.client;

/**
 * Provider-independent failure categories exposed by a place client implementation.
 */
public enum PlaceClientFailure {
	INVALID_REQUEST(false),
	AUTHENTICATION_FAILED(false),
	ACCESS_DENIED(false),
	RATE_LIMITED(false),
	TIMEOUT(true),
	CONNECTION_FAILED(true),
	PROVIDER_UNAVAILABLE(true),
	INVALID_RESPONSE(false);

	private final boolean technicalFailure;

	PlaceClientFailure(boolean technicalFailure) {
		this.technicalFailure = technicalFailure;
	}

	public boolean isTechnicalFailure() {
		return technicalFailure;
	}
}
