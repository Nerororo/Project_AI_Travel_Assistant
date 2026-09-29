package com.example.travel.place.dto;

import java.net.URI;
import java.util.List;

public record HotelSearchApiResponse(List<Place> places, int page, boolean hasNext) {
	public HotelSearchApiResponse {
		places = List.copyOf(places);
	}

	public record Place(
			String kakaoPlaceId,
			URI placeUrl,
			String providerDisplayName,
			String address,
			double latitude,
			double longitude,
			String selectionToken
	) {
	}
}
