package com.example.travel.route.client;

/**
 * Normal outcome of a route lookup. Provider payloads, paths and coordinates are not retained.
 */
public sealed interface RouteResult permits RouteResult.Found, RouteResult.NotFound {

	static RouteResult found(long durationSeconds) {
		return new Found(durationSeconds);
	}

	static RouteResult notFound() {
		return new NotFound();
	}

	/**
	 * Successful provider estimate before the service applies the required ten-minute rounding.
	 */
	record Found(long durationSeconds) implements RouteResult {

		public Found {
			if (durationSeconds < 0) {
				throw new IllegalArgumentException("durationSeconds must not be negative");
			}
		}
	}

	/**
	 * A valid provider response stating that no route exists for the segment.
	 */
	record NotFound() implements RouteResult {
	}
}
