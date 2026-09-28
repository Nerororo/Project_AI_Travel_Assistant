package com.example.travel.route.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

abstract class FakeRouteClientSupport implements RouteClient {

	private RouteResult result;
	private RouteClientFailure failure;
	private RouteSegment lastSegment;
	private final List<RouteSegment> receivedSegments = new ArrayList<>();
	private final Deque<RouteResult> queuedResults = new ArrayDeque<>();
	private int callCount;

	public void willReturn(RouteResult result) {
		this.result = Objects.requireNonNull(result, "result must not be null");
		this.failure = null;
	}

	public void willFailWith(RouteClientFailure failure) {
		this.failure = Objects.requireNonNull(failure, "failure must not be null");
		this.result = null;
	}

	public void willReturnInOrder(RouteResult... results) {
		Objects.requireNonNull(results, "results must not be null");
		queuedResults.clear();
		for (RouteResult queuedResult : results) {
			queuedResults.addLast(Objects.requireNonNull(queuedResult, "results must not contain null"));
		}
		this.result = null;
		this.failure = null;
	}

	@Override
	public RouteResult findRoute(RouteSegment segment) {
		lastSegment = Objects.requireNonNull(segment, "segment must not be null");
		receivedSegments.add(segment);
		callCount++;
		if (failure != null) {
			throw new RouteClientException(failure);
		}
		if (!queuedResults.isEmpty()) {
			return queuedResults.removeFirst();
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

	public List<RouteSegment> receivedSegments() {
		return List.copyOf(receivedSegments);
	}
}
