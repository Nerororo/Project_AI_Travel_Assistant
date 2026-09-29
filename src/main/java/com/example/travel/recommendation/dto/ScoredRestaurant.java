package com.example.travel.recommendation.dto;

/** Lower score means less additional straight-line travel. */
public record ScoredRestaurant(
		RestaurantCandidate candidate,
		double detourKilometers,
		int estimatedDetourMinutes
) {
}
