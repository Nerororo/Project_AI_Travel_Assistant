package com.example.travel.route.algorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Improves a deterministic open route while preserving both boundary points.
 */
public final class TwoOptRoute {

	private static final double IMPROVEMENT_TOLERANCE_KILOMETERS = 1e-6;
	private static final int MAX_ITERATIONS = 1_000;

	private TwoOptRoute() {
	}

	public static List<RoutePoint> improve(List<RoutePoint> route) {
		return improve(route, MAX_ITERATIONS);
	}

	static List<RoutePoint> improve(List<RoutePoint> route, int maxIterations) {
		Objects.requireNonNull(route, "route must not be null");
		if (maxIterations <= 0) {
			throw new IllegalArgumentException("maxIterations must be positive");
		}

		List<RoutePoint> input = List.copyOf(route);
		validateUniqueKeys(input);
		if (input.size() < 4) {
			return input;
		}

		List<RoutePoint> improved = new ArrayList<>(input);
		for (int iteration = 0; iteration < maxIterations; iteration++) {
			Reversal best = findBestReversal(improved);
			if (best == null) {
				break;
			}
			reverse(improved, best.fromInclusive(), best.toInclusive());
		}

		return List.copyOf(improved);
	}

	private static Reversal findBestReversal(List<RoutePoint> route) {
		Reversal best = null;
		double bestImprovement = 0.0;

		for (int from = 1; from < route.size() - 2; from++) {
			for (int to = from + 1; to < route.size() - 1; to++) {
				double improvement = improvementFor(route, from, to);
				boolean firstImprovement = best == null
						&& improvement > IMPROVEMENT_TOLERANCE_KILOMETERS;
				boolean betterImprovement = best != null
						&& improvement > bestImprovement + IMPROVEMENT_TOLERANCE_KILOMETERS;
				if (firstImprovement || betterImprovement) {
					best = new Reversal(from, to);
					bestImprovement = improvement;
				}
			}
		}

		return best;
	}

	private static double improvementFor(List<RoutePoint> route, int from, int to) {
		RoutePoint before = route.get(from - 1);
		RoutePoint first = route.get(from);
		RoutePoint last = route.get(to);

		double removed = distance(before, first);
		double added = distance(before, last);
		if (to + 1 < route.size()) {
			RoutePoint after = route.get(to + 1);
			removed += distance(last, after);
			added += distance(first, after);
		}
		return removed - added;
	}

	private static double distance(RoutePoint from, RoutePoint to) {
		return HaversineDistance.kilometers(from.coordinate(), to.coordinate());
	}

	private static void reverse(List<RoutePoint> route, int from, int to) {
		while (from < to) {
			RoutePoint temporary = route.get(from);
			route.set(from, route.get(to));
			route.set(to, temporary);
			from++;
			to--;
		}
	}

	private static void validateUniqueKeys(List<RoutePoint> route) {
		Set<String> keys = new HashSet<>();
		for (RoutePoint point : route) {
			if (!keys.add(point.stableKey())) {
				throw new IllegalArgumentException("stableKey must be unique within route");
			}
		}
	}

	private record Reversal(int fromInclusive, int toInclusive) {
	}
}
