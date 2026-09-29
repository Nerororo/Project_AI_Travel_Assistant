package com.example.travel.place.dto;

import java.net.URI;

/** Verified provider fields used only during the current planning request. */
public record PlanningPlaceSelection(
		String kakaoPlaceId,
		URI placeUrl,
		double latitude,
		double longitude
) {
}
