package com.example.travel.route.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a deterministic open route from a caller-selected start point.
 */
public final class NearestNeighborRoute {

	private static final double DISTANCE_TIE_TOLERANCE_KILOMETERS = 1e-6;

	private NearestNeighborRoute() {
	}

	public static List<RoutePoint> order(List<RoutePoint> places, Optional<String> startKey) {
		Objects.requireNonNull(places, "places must not be null");
		Objects.requireNonNull(startKey, "startKey must not be null");

		List<RoutePoint> input = List.copyOf(places);
		validateUniqueKeys(input);

		if (input.isEmpty()) {
			if (startKey.isPresent()) {
				throw new IllegalArgumentException("startKey must be empty when places are empty");
			}
			return List.of();
		}

		String requiredStartKey = startKey.orElseThrow(
				() -> new IllegalArgumentException("startKey is required when places are not empty"));
		RoutePoint start = input.stream()
				.filter(place -> place.stableKey().equals(requiredStartKey))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("startKey must identify a place in the input"));

		List<RoutePoint> unvisited = new ArrayList<>(input);
		unvisited.remove(start);
		unvisited.sort(Comparator.comparing(RoutePoint::stableKey));

		List<RoutePoint> ordered = new ArrayList<>(input.size());
		ordered.add(start);
		RoutePoint current = start;

		while (!unvisited.isEmpty()) {
			RoutePoint next = findNearest(current, unvisited);
			ordered.add(next);
			unvisited.remove(next);
			current = next;
		}

		return List.copyOf(ordered);
	}

	private static void validateUniqueKeys(List<RoutePoint> places) {
		Set<String> keys = new HashSet<>();
		for (RoutePoint place : places) {
			if (!keys.add(place.stableKey())) {
				throw new IllegalArgumentException("stableKey must be unique within places");
			}
		}
	}

	private static RoutePoint findNearest(RoutePoint current, List<RoutePoint> candidates) {
		RoutePoint nearest = null;
		double nearestDistance = Double.POSITIVE_INFINITY;

		for (RoutePoint candidate : candidates) {
			double distance = HaversineDistance.kilometers(current.coordinate(), candidate.coordinate());
			if (distance < nearestDistance - DISTANCE_TIE_TOLERANCE_KILOMETERS) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}

		return nearest;
	}
}
