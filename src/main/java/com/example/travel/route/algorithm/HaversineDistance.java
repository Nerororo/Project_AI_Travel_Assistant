package com.example.travel.route.algorithm;

import java.util.Objects;

/**
 * Calculates great-circle distance in kilometres using the mean Earth radius.
 */
public final class HaversineDistance {

	static final double EARTH_RADIUS_KILOMETERS = 6_371.0088;

	private HaversineDistance() {
	}

	public static double kilometers(Coordinate from, Coordinate to) {
		Objects.requireNonNull(from, "from must not be null");
		Objects.requireNonNull(to, "to must not be null");

		if (from.equals(to)) {
			return 0.0;
		}

		double fromLatitude = Math.toRadians(from.latitude());
		double toLatitude = Math.toRadians(to.latitude());
		double latitudeDelta = toLatitude - fromLatitude;
		double longitudeDelta = Math.toRadians(to.longitude() - from.longitude());

		double latitudeTerm = Math.sin(latitudeDelta / 2.0);
		double longitudeTerm = Math.sin(longitudeDelta / 2.0);
		double haversine = latitudeTerm * latitudeTerm
				+ Math.cos(fromLatitude) * Math.cos(toLatitude) * longitudeTerm * longitudeTerm;
		double boundedHaversine = Math.clamp(haversine, 0.0, 1.0);
		double centralAngle = 2.0 * Math.asin(Math.sqrt(boundedHaversine));

		return EARTH_RADIUS_KILOMETERS * centralAngle;
	}
}
