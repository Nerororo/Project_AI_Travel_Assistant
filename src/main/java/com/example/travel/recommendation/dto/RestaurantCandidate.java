package com.example.travel.recommendation.dto;

import com.example.travel.route.algorithm.Coordinate;
import java.util.Objects;

/** Temporary provider candidate; callers discard it after the request. */
public record RestaurantCandidate(String placeId, Coordinate coordinate) {
	public RestaurantCandidate {
		if (placeId == null || placeId.isBlank()) {
			throw new IllegalArgumentException("placeId must not be blank");
		}
		Objects.requireNonNull(coordinate, "coordinate must not be null");
	}
}
