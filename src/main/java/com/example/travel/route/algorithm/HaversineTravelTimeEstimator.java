package com.example.travel.route.algorithm;

import java.util.Objects;

/**
 * Estimates unverified travel time and rounds it up to ten-minute units.
 */
public final class HaversineTravelTimeEstimator {

	private static final int MINUTES_PER_HOUR = 60;
	private static final int ROUNDING_UNIT_MINUTES = 10;
	private final TravelTimePolicy policy;

	public HaversineTravelTimeEstimator(TravelTimePolicy policy) {
		this.policy = Objects.requireNonNull(policy, "policy must not be null");
	}

	public TravelMode travelMode() {
		return policy.travelMode();
	}

	public int estimateMinutes(
			Coordinate from,
			Coordinate to
	) {
		Objects.requireNonNull(from, "from must not be null");
		Objects.requireNonNull(to, "to must not be null");
		return estimateMinutes(HaversineDistance.kilometers(from, to));
	}

	public int estimateMinutes(double distanceKilometers) {
		if (!Double.isFinite(distanceKilometers) || distanceKilometers < 0.0) {
			throw new IllegalArgumentException("distanceKilometers must be finite and non-negative");
		}
		if (distanceKilometers == 0.0) {
			return 0;
		}

		double rawMinutes = distanceKilometers
				* policy.detourFactor()
				/ policy.averageSpeedKilometersPerHour()
				* MINUTES_PER_HOUR;
		double roundedMinutes = Math.ceil(rawMinutes / ROUNDING_UNIT_MINUTES)
				* ROUNDING_UNIT_MINUTES;
		if (!Double.isFinite(roundedMinutes) || roundedMinutes > Integer.MAX_VALUE) {
			throw new ArithmeticException("estimated travel time exceeds supported minutes");
		}
		return (int) roundedMinutes;
	}
}
