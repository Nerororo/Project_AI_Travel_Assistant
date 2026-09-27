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
	private final Deque<Behavior> queuedBehaviors = new ArrayDeque<>();
	private int callCount;

	public void willReturn(RouteResult result) {
		queuedBehaviors.clear();
		this.result = Objects.requireNonNull(result, "result must not be null");
		this.failure = null;
	}

	public void willFailWith(RouteClientFailure failure) {
		queuedBehaviors.clear();
		this.failure = Objects.requireNonNull(failure, "failure must not be null");
		this.result = null;
	}

	public void willReturnInOrder(RouteResult... results) {
		Objects.requireNonNull(results, "results must not be null");
		queuedBehaviors.clear();
		for (RouteResult queuedResult : results) {
			queuedBehaviors.addLast(Behavior.returning(
					Objects.requireNonNull(queuedResult, "results must not contain null")));
		}
		this.result = null;
		this.failure = null;
	}

	public void willFailThenReturn(RouteClientFailure failure, RouteResult result) {
		queuedBehaviors.clear();
		queuedBehaviors.addLast(Behavior.failing(
				Objects.requireNonNull(failure, "failure must not be null")));
		queuedBehaviors.addLast(Behavior.returning(
				Objects.requireNonNull(result, "result must not be null")));
		this.result = null;
		this.failure = null;
	}

	public void willReturnThenFail(RouteResult result, RouteClientFailure failure) {
		queuedBehaviors.clear();
		queuedBehaviors.addLast(Behavior.returning(
				Objects.requireNonNull(result, "result must not be null")));
		queuedBehaviors.addLast(Behavior.failing(
				Objects.requireNonNull(failure, "failure must not be null")));
		this.result = null;
		this.failure = failure;
	}

	@Override
	public RouteResult findRoute(RouteSegment segment) {
		lastSegment = Objects.requireNonNull(segment, "segment must not be null");
		receivedSegments.add(segment);
		callCount++;
		if (!queuedBehaviors.isEmpty()) {
			return queuedBehaviors.removeFirst().execute();
		}
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

	public List<RouteSegment> receivedSegments() {
		return List.copyOf(receivedSegments);
	}

	private record Behavior(RouteResult result, RouteClientFailure failure) {

		private static Behavior returning(RouteResult result) {
			return new Behavior(result, null);
		}

		private static Behavior failing(RouteClientFailure failure) {
			return new Behavior(null, failure);
		}

		private RouteResult execute() {
			if (failure != null) {
				throw new RouteClientException(failure);
			}
			return result;
		}
	}
}
