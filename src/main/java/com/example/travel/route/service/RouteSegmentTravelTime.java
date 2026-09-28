package com.example.travel.route.service;

/**
 * Route verification outcome for one adjacent segment, without provider details or coordinates.
 */
public sealed interface RouteSegmentTravelTime
		permits RouteSegmentTravelTime.Found, RouteSegmentTravelTime.NotFound {

	static RouteSegmentTravelTime found(int estimatedMinutes) {
		return new Found(estimatedMinutes);
	}

	static RouteSegmentTravelTime notFound() {
		return new NotFound();
	}

	record Found(int estimatedMinutes) implements RouteSegmentTravelTime {

		public Found {
			if (estimatedMinutes < 0 || estimatedMinutes % 10 != 0) {
				throw new IllegalArgumentException("estimatedMinutes must be a non-negative multiple of 10");
			}
		}
	}

	record NotFound() implements RouteSegmentTravelTime {
	}
}
