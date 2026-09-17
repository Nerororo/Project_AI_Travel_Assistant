package com.example.travel.route.algorithm;

import java.util.Objects;

/**
 * A uniquely keyed coordinate used during one in-memory route calculation.
 */
public record RoutePoint(String stableKey, Coordinate coordinate) {

	public RoutePoint {
		Objects.requireNonNull(stableKey, "stableKey must not be null");
		Objects.requireNonNull(coordinate, "coordinate must not be null");
		if (stableKey.isEmpty()) {
			throw new IllegalArgumentException("stableKey must not be empty");
		}
	}
}
