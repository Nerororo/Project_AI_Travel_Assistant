package com.example.travel.recommendation.service;

import com.example.travel.recommendation.dto.RestaurantCandidate;
import com.example.travel.recommendation.dto.RestaurantRecommendationRequest;
import com.example.travel.recommendation.dto.RestaurantRecommendationResult;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.MealSlot;
import com.example.travel.travelplan.domain.MealType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantRecommendationServiceTest {
	private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
	private static final Coordinate BEFORE = new Coordinate(0, 0);
	private static final Coordinate AFTER = new Coordinate(0, 0.02);
	private final RestaurantRecommendationService service = new RestaurantRecommendationService();

	@Test
	void ranksByAdditionalHaversineDistanceAndBreaksTiesById() {
		RestaurantCandidate offRoute = candidate("z", 0.005, 0.01);
		RestaurantCandidate onRouteB = candidate("b", 0, 0.01);
		RestaurantCandidate onRouteA = candidate("a", 0, 0.01);
		RestaurantRecommendationResult result = service.recommend(request(
				List.of(offRoute, onRouteB, onRouteA)));

		assertThat(result.status()).isEqualTo(RestaurantRecommendationResult.Status.FOUND);
		assertThat(result.restaurants()).extracting(item -> item.candidate().placeId())
				.containsExactly("a", "b", "z");
		assertThat(result.restaurants().get(0).detourKilometers()).isZero();
		assertThat(result.restaurants().get(2).detourKilometers()).isPositive();
		assertThat(result.restaurants().get(2).estimatedDetourMinutes()).isPositive();
		assertThat(service.recommend(request(List.of(onRouteA, offRoute, onRouteB))).restaurants())
				.isEqualTo(result.restaurants());
	}

	@Test
	void excludesCandidatesThatCannotReachMealOrNextPlaceOnTime() {
		RestaurantCandidate near = candidate("near", 0, 0.01);
		RestaurantCandidate lateArrival = candidate("late", 0, 1);
		RestaurantRecommendationResult result = service.recommend(request(List.of(lateArrival, near)));
		assertThat(result.restaurants()).extracting(item -> item.candidate().placeId())
				.containsExactly("near");

		RestaurantRecommendationRequest tightAfter = new RestaurantRecommendationRequest(slot(),
				DATE.atTime(11, 30), DATE.atTime(13, 0), BEFORE, AFTER, TravelMode.CAR,
				RestaurantRecommendationRequest.LookupOutcome.SUCCESS, List.of(near));
		assertThat(service.recommend(tightAfter).status())
				.isEqualTo(RestaurantRecommendationResult.Status.NO_CANDIDATES);
	}

	@Test
	void separatesEmptyAndProviderFailureWithoutSelectingAnything() {
		assertThat(service.recommend(request(List.of())).status())
				.isEqualTo(RestaurantRecommendationResult.Status.NO_CANDIDATES);
		RestaurantRecommendationRequest failure = new RestaurantRecommendationRequest(slot(),
				DATE.atTime(11, 30), DATE.atTime(13, 30), BEFORE, AFTER, TravelMode.CAR,
				RestaurantRecommendationRequest.LookupOutcome.PROVIDER_FAILURE, List.of());
		RestaurantRecommendationResult result = service.recommend(failure);
		assertThat(result.status()).isEqualTo(RestaurantRecommendationResult.Status.PROVIDER_FAILURE);
		assertThat(result.restaurants()).isEmpty();
	}

	@Test
	void rejectsMealOutsideItsWindow() {
		MealSlot invalidLunch = new MealSlot(DATE, MealType.LUNCH, LocalTime.of(14, 0),
				LocalTime.of(15, 0));
		RestaurantRecommendationRequest request = new RestaurantRecommendationRequest(invalidLunch,
				DATE.atTime(11, 30), DATE.atTime(16, 0), BEFORE, AFTER, TravelMode.CAR,
				RestaurantRecommendationRequest.LookupOutcome.SUCCESS, List.of());
		assertThatThrownBy(() -> service.recommend(request)).isInstanceOf(IllegalArgumentException.class);
	}

	private RestaurantRecommendationRequest request(List<RestaurantCandidate> candidates) {
		return new RestaurantRecommendationRequest(slot(), DATE.atTime(11, 30), DATE.atTime(13, 30),
				BEFORE, AFTER, TravelMode.CAR,
				RestaurantRecommendationRequest.LookupOutcome.SUCCESS, candidates);
	}

	private MealSlot slot() {
		return new MealSlot(DATE, MealType.LUNCH, LocalTime.NOON, LocalTime.of(13, 0));
	}

	private RestaurantCandidate candidate(String id, double latitude, double longitude) {
		return new RestaurantCandidate(id, new Coordinate(latitude, longitude));
	}
}
