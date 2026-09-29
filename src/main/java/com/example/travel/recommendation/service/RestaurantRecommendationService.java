package com.example.travel.recommendation.service;

import com.example.travel.recommendation.dto.RestaurantCandidate;
import com.example.travel.recommendation.dto.RestaurantRecommendationRequest;
import com.example.travel.recommendation.dto.RestaurantRecommendationResult;
import com.example.travel.recommendation.dto.ScoredRestaurant;
import com.example.travel.route.algorithm.HaversineDistance;
import com.example.travel.route.algorithm.HaversineTravelTimeEstimator;
import com.example.travel.route.algorithm.TravelTimePolicy;
import com.example.travel.travelplan.domain.MealSlot;
import com.example.travel.travelplan.domain.MealType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Pure, request-scoped ranking; no provider access or persisted choice. */
@Service
public final class RestaurantRecommendationService {
	public RestaurantRecommendationResult recommend(RestaurantRecommendationRequest request) {
		MealSlot slot = request.mealSlot();
		MealType type = slot.mealType();
		LocalDateTime mealStart = slot.date().atTime(slot.startTime());
		LocalDateTime mealEnd = slot.date().atTime(slot.endTime());
		if (slot.startTime().isBefore(type.allowedStart())
				|| slot.endTime().isAfter(type.allowedEnd())
				|| request.previousPlaceAvailableAt().isAfter(mealStart)
				|| request.nextPlaceRequiredAt().isBefore(mealEnd)) {
			throw new IllegalArgumentException("meal context is outside its available time window");
		}
		if (request.lookupOutcome() == RestaurantRecommendationRequest.LookupOutcome.PROVIDER_FAILURE) {
			return new RestaurantRecommendationResult(RestaurantRecommendationResult.Status.PROVIDER_FAILURE, List.of());
		}

		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(
				TravelTimePolicy.defaultFor(request.travelMode()));
		double directKilometers = HaversineDistance.kilometers(request.previousPlace(), request.nextPlace());
		List<ScoredRestaurant> ranked = new ArrayList<>();
		for (RestaurantCandidate candidate : request.candidates()) {
			double beforeKilometers = HaversineDistance.kilometers(request.previousPlace(), candidate.coordinate());
			double afterKilometers = HaversineDistance.kilometers(candidate.coordinate(), request.nextPlace());
			if (request.previousPlaceAvailableAt().plusMinutes(estimator.estimateMinutes(beforeKilometers))
					.isAfter(mealStart)
					|| mealEnd.plusMinutes(estimator.estimateMinutes(afterKilometers))
					.isAfter(request.nextPlaceRequiredAt())) {
				continue;
			}
			double detourKilometers = Math.max(0.0, beforeKilometers + afterKilometers - directKilometers);
			ranked.add(new ScoredRestaurant(candidate, detourKilometers,
					estimator.estimateMinutes(detourKilometers)));
		}
		ranked.sort(Comparator.comparingDouble(ScoredRestaurant::detourKilometers)
				.thenComparing(item -> item.candidate().placeId())
				.thenComparingDouble(item -> item.candidate().coordinate().latitude())
				.thenComparingDouble(item -> item.candidate().coordinate().longitude()));
		return new RestaurantRecommendationResult(
				ranked.isEmpty() ? RestaurantRecommendationResult.Status.NO_CANDIDATES
						: RestaurantRecommendationResult.Status.FOUND, ranked);
	}
}
