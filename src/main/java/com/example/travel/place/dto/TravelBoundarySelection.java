package com.example.travel.place.dto;

import java.net.URI;

/**
 * A verified travel boundary that may exist only in the current planning request.
 */
public record TravelBoundarySelection(
		String kakaoPlaceId,
		URI placeUrl,
		double latitude,
		double longitude
) {
}
