package com.example.travel.route.service;

import java.util.List;
import java.util.Objects;

/**
 * Request-scoped route result plus non-persistent warning and aggregate observation contracts.
 */
public record RouteVerificationResult(
		RouteTravelTimeResult travelTimes,
		List<RouteWarning> warnings,
		RouteObservationEvent observationEvent
) {

	public RouteVerificationResult {
		Objects.requireNonNull(travelTimes, "travelTimes must not be null");
		Objects.requireNonNull(warnings, "warnings must not be null");
		warnings = List.copyOf(warnings);
		Objects.requireNonNull(observationEvent, "observationEvent must not be null");
	}
}
