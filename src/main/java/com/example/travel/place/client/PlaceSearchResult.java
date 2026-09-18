package com.example.travel.place.client;

import java.util.List;
import java.util.Objects;

/**
 * A successful provider response. An empty {@code places} list is a normal result, not a failure.
 */
public record PlaceSearchResult(List<PlaceCandidate> places, int page, boolean hasNext) {

	public PlaceSearchResult {
		Objects.requireNonNull(places, "places must not be null");
		if (places.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("places must not contain null");
		}
		places = List.copyOf(places);
		if (page < 1) {
			throw new IllegalArgumentException("page must be at least 1");
		}
	}

	public static PlaceSearchResult empty(int page) {
		return new PlaceSearchResult(List.of(), page, false);
	}
}
