package com.example.travel.place.domain;

import java.util.List;
import java.util.Objects;

/**
 * Provider search radii allowed for each place role, in expansion order.
 */
public final class PlaceSearchRadiusPolicy {

	private static final List<Integer> ATTRACTION_RADII = List.of(20_000);
	private static final List<Integer> HOTEL_RADII = List.of(5_000, 10_000);
	private static final List<Integer> RESTAURANT_RADII = List.of(1_000, 3_000, 5_000);

	private PlaceSearchRadiusPolicy() {
	}

	public static List<Integer> radiiMeters(PlaceRole role) {
		return switch (Objects.requireNonNull(role, "role must not be null")) {
			case ATTRACTION -> ATTRACTION_RADII;
			case HOTEL -> HOTEL_RADII;
			case RESTAURANT -> RESTAURANT_RADII;
		};
	}
}
