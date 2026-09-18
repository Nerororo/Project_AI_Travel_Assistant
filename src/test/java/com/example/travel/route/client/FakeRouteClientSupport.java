package com.example.travel.route.client;

import java.util.Objects;

abstract class FakeRouteClientSupport implements RouteClient {

	private RouteResult result;
	private RouteClientFailure failure;
	private RouteSegment lastSegment;
	private int callCount;

	public void willReturn(RouteResult result) {
		this.result = Objects.requireNonNull(result, "result must not be null");
		this.failure = null;
	}

	public void willFailWith(RouteClientFailure failure) {
		this.failure = Objects.requireNonNull(failure, "failure must not be null");
		this.result = null;
	}

	@Override
	public RouteResult findRoute(RouteSegment segment) {
		lastSegment = Objects.requireNonNull(segment, "segment must not be null");
		callCount++;
		if (failure != null) {
			throw new RouteClientException(failure);
		}
		if (result == null) {
			throw new IllegalStateException("Fake route client behavior is not configured");
		}
		return result;
	}

	public RouteSegment lastSegment() {
		return lastSegment;
	}

	public int callCount() {
		return callCount;
	}
}
