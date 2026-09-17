package com.example.travel.route.algorithm;

/**
 * WGS84 latitude and longitude used only for in-memory route calculations.
 */
public record Coordinate(double latitude, double longitude) {

	public Coordinate {
		validateFinite("latitude", latitude);
		validateFinite("longitude", longitude);
		if (latitude < -90.0 || latitude > 90.0) {
			throw new IllegalArgumentException("latitude must be between -90 and 90");
		}
		if (longitude < -180.0 || longitude > 180.0) {
			throw new IllegalArgumentException("longitude must be between -180 and 180");
		}
		latitude = normalizeZero(latitude);
		longitude = normalizeZero(longitude);
	}

	private static void validateFinite(String name, double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}

	private static double normalizeZero(double value) {
		return value == 0.0 ? 0.0 : value;
	}
}
