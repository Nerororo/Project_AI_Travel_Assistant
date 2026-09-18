package com.example.travel.route.algorithm;

import java.util.Objects;

/**
 * Immutable coefficients for Haversine-based initial travel-time estimates.
 */
public record TravelTimePolicy(
		TravelMode travelMode,
		double detourFactor,
		double averageSpeedKilometersPerHour
) {

	private static final double MINIMUM_DETOUR_FACTOR = 1.0;
	private static final double MAXIMUM_DETOUR_FACTOR = 3.0;
	private static final double MAXIMUM_AVERAGE_SPEED_KILOMETERS_PER_HOUR = 200.0;

	public TravelTimePolicy {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		validateFinite("detourFactor", detourFactor);
		validateFinite("averageSpeedKilometersPerHour", averageSpeedKilometersPerHour);
		if (detourFactor < MINIMUM_DETOUR_FACTOR || detourFactor > MAXIMUM_DETOUR_FACTOR) {
			throw new IllegalArgumentException("detourFactor must be between 1.0 and 3.0");
		}
		if (averageSpeedKilometersPerHour <= 0.0
				|| averageSpeedKilometersPerHour > MAXIMUM_AVERAGE_SPEED_KILOMETERS_PER_HOUR) {
			throw new IllegalArgumentException(
					"averageSpeedKilometersPerHour must be greater than 0.0 and at most 200.0");
		}
	}

	public static TravelTimePolicy defaultFor(TravelMode travelMode) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		return switch (travelMode) {
			case CAR -> new TravelTimePolicy(TravelMode.CAR, 1.30, 60.0);
			case PUBLIC_TRANSIT -> new TravelTimePolicy(TravelMode.PUBLIC_TRANSIT, 1.40, 50.0);
		};
	}

	private static void validateFinite(String name, double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
