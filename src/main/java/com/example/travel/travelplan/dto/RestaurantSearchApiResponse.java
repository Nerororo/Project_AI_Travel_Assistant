package com.example.travel.travelplan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record RestaurantSearchApiResponse(
		LocalDate mealDate,
		@JsonFormat(pattern = "HH:mm") LocalTime mealStartTime,
		@JsonFormat(pattern = "HH:mm") LocalTime mealEndTime,
		Reference previousPlace,
		Reference nextPlace,
		Reference referenceAttraction,
		List<Place> places,
		String emptyReason,
		int page,
		boolean hasNext
) {
	public RestaurantSearchApiResponse {
		places = List.copyOf(places);
	}

	public record Reference(String kind, UUID clientPlaceId, double latitude, double longitude) {
	}

	public record Place(String kakaoPlaceId, URI placeUrl, String providerDisplayName,
			String address, double latitude, double longitude, String selectionToken,
			double detourKilometers, int estimatedDetourMinutes) {
	}
}
