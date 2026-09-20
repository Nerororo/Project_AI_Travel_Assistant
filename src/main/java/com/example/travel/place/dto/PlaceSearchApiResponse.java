package com.example.travel.place.dto;

import java.net.URI;
import java.util.List;

public record PlaceSearchApiResponse(List<Place> places, int page, boolean hasNext) {

	public PlaceSearchApiResponse {
		places = List.copyOf(places);
	}

	public record Place(
			String kakaoPlaceId,
			URI placeUrl,
			String providerDisplayName,
			String address,
			double latitude,
			double longitude,
			Integer suggestedStayMinutes,
			String selectionToken
	) {
	}
}
