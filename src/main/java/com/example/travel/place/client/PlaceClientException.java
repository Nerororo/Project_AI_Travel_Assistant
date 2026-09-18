package com.example.travel.place.client;

import java.util.Objects;

/**
 * Safe place-client exception containing only a normalized failure category.
 * Provider response bodies, request payloads and coordinates must not be attached to this exception.
 */
public final class PlaceClientException extends RuntimeException {

	private final PlaceClientFailure failure;

	public PlaceClientException(PlaceClientFailure failure) {
		super(Objects.requireNonNull(failure, "failure must not be null").name());
		this.failure = failure;
	}

	public PlaceClientFailure failure() {
		return failure;
	}
}
