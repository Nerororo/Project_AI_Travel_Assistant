package com.example.travel.recommendation.dto;

import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.MealSlot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Recalculated meal context and an ephemeral provider lookup outcome. */
public record RestaurantRecommendationRequest(
		MealSlot mealSlot,
		LocalDateTime previousPlaceAvailableAt,
		LocalDateTime nextPlaceRequiredAt,
		Coordinate previousPlace,
		Coordinate nextPlace,
		TravelMode travelMode,
		LookupOutcome lookupOutcome,
		List<RestaurantCandidate> candidates
) {
	public enum LookupOutcome { SUCCESS, PROVIDER_FAILURE }

	public RestaurantRecommendationRequest {
		Objects.requireNonNull(mealSlot, "mealSlot must not be null");
		Objects.requireNonNull(previousPlaceAvailableAt, "previousPlaceAvailableAt must not be null");
		Objects.requireNonNull(nextPlaceRequiredAt, "nextPlaceRequiredAt must not be null");
		Objects.requireNonNull(previousPlace, "previousPlace must not be null");
		Objects.requireNonNull(nextPlace, "nextPlace must not be null");
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(lookupOutcome, "lookupOutcome must not be null");
		candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
		if (lookupOutcome == LookupOutcome.PROVIDER_FAILURE && !candidates.isEmpty()) {
			throw new IllegalArgumentException("failed lookup cannot contain candidates");
		}
	}
}
