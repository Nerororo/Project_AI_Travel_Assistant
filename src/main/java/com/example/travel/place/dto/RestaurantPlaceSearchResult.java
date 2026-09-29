package com.example.travel.place.dto;

import com.example.travel.route.algorithm.Coordinate;
import java.net.URI;
import java.util.List;

/** Temporary, address-checked candidates returned to the travel-plan coordinator. */
public record RestaurantPlaceSearchResult(List<Place> places, int page, boolean hasNext) {
	public RestaurantPlaceSearchResult {
		places = List.copyOf(places);
	}

	public record Place(String kakaoPlaceId, URI placeUrl, String providerDisplayName,
			String address, Coordinate coordinate, String selectionToken) {
	}
}
