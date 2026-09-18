package com.example.travel.route.client;

import java.util.Objects;

/**
 * Safe route-client exception containing no provider payload, coordinates or original duration.
 */
public final class RouteClientException extends RuntimeException {

	private final RouteClientFailure failure;

	public RouteClientException(RouteClientFailure failure) {
		super(Objects.requireNonNull(failure, "failure must not be null").name());
		this.failure = failure;
	}

	public RouteClientFailure failure() {
		return failure;
	}
}
