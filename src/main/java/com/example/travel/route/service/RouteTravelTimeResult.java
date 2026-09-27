package com.example.travel.route.service;

import java.util.List;
import java.util.Objects;

/**
 * Request-scoped route verification result. The fallback flag is for the create-response warning
 * and must not be persisted with an itinerary.
 */
public record RouteTravelTimeResult(
		List<RouteSegmentTravelTime> segmentTravelTimes,
		boolean fallbackApplied
) {

	public RouteTravelTimeResult {
		Objects.requireNonNull(segmentTravelTimes, "segmentTravelTimes must not be null");
		segmentTravelTimes = List.copyOf(segmentTravelTimes);
	}

	public static RouteTravelTimeResult verified(List<RouteSegmentTravelTime> segmentTravelTimes) {
		return new RouteTravelTimeResult(segmentTravelTimes, false);
	}

	public static RouteTravelTimeResult fallback(List<RouteSegmentTravelTime> segmentTravelTimes) {
		return new RouteTravelTimeResult(segmentTravelTimes, true);
	}
}
