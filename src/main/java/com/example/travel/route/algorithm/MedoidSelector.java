package com.example.travel.route.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Selects the input point with the smallest total Haversine distance.
 */
public final class MedoidSelector {

	private static final double DISTANCE_TIE_TOLERANCE_KILOMETERS = 1e-6;

	private MedoidSelector() {
	}

	public static RoutePoint select(List<RoutePoint> points) {
		Objects.requireNonNull(points, "points must not be null");
		List<RoutePoint> input = new ArrayList<>(List.copyOf(points));
		if (input.isEmpty()) {
			throw new IllegalArgumentException("points must not be empty");
		}
		validateUniqueKeys(input);
		input.sort(Comparator.comparing(RoutePoint::stableKey));

		RoutePoint medoid = null;
		double lowestDistanceSum = Double.POSITIVE_INFINITY;
		for (RoutePoint candidate : input) {
			double distanceSum = totalDistance(candidate, input);
			if (distanceSum < lowestDistanceSum - DISTANCE_TIE_TOLERANCE_KILOMETERS) {
				medoid = candidate;
				lowestDistanceSum = distanceSum;
			}
		}
		return medoid;
	}

	private static double totalDistance(RoutePoint candidate, List<RoutePoint> points) {
		double total = 0.0;
		for (RoutePoint point : points) {
			total += HaversineDistance.kilometers(candidate.coordinate(), point.coordinate());
		}
		return total;
	}

	private static void validateUniqueKeys(List<RoutePoint> points) {
		Set<String> keys = new HashSet<>();
		for (RoutePoint point : points) {
			if (!keys.add(point.stableKey())) {
				throw new IllegalArgumentException("stableKey must be unique within points");
			}
		}
	}
}
