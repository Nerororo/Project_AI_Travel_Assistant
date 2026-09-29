package com.example.travel.recommendation.dto;

import java.util.List;
import java.util.Objects;

public record RestaurantRecommendationResult(Status status, List<ScoredRestaurant> restaurants) {
	public enum Status { FOUND, NO_CANDIDATES, PROVIDER_FAILURE }

	public RestaurantRecommendationResult {
		Objects.requireNonNull(status, "status must not be null");
		restaurants = List.copyOf(Objects.requireNonNull(restaurants, "restaurants must not be null"));
	}
}
