package com.example.travel.route.client;

import java.util.Objects;

/**
 * One ordered pair of adjacent coordinates used only during the current route request.
 */
public record RouteSegment(Endpoint origin, Endpoint destination) {

	public RouteSegment {
		Objects.requireNonNull(origin, "origin must not be null");
		Objects.requireNonNull(destination, "destination must not be null");
	}

	/**
	 * WGS84 coordinate that must not be persisted, cached or logged.
	 */
	public record Endpoint(double latitude, double longitude) {

		public Endpoint {
			if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
				throw new IllegalArgumentException("latitude must be finite and between -90 and 90");
			}
			if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
				throw new IllegalArgumentException("longitude must be finite and between -180 and 180");
			}
			latitude = normalizeZero(latitude);
			longitude = normalizeZero(longitude);
		}

		private static double normalizeZero(double value) {
			return value == 0.0 ? 0.0 : value;
		}
	}
}
