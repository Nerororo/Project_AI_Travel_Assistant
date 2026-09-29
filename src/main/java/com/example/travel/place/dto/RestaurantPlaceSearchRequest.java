package com.example.travel.place.dto;

import com.example.travel.route.algorithm.Coordinate;

/** Verified search center or user-selected map area for one creation request. */
public record RestaurantPlaceSearchRequest(
		String regionId, String query, Coordinate center, Bounds bounds, int page, int size
) {
	public record Bounds(double minLatitude, double minLongitude,
			double maxLatitude, double maxLongitude) {
	}
}
