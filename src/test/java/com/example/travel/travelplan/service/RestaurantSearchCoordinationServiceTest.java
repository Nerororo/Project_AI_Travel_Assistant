package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.RestaurantPlaceSearchRequest;
import com.example.travel.place.dto.RestaurantPlaceSearchResult;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.RestaurantSearchService;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.recommendation.service.RestaurantRecommendationService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateResult;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.ResolvedEstimate;
import com.example.travel.travelplan.dto.RestaurantSearchApiRequest;
import com.example.travel.travelplan.dto.TravelConditions;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestaurantSearchCoordinationServiceTest {
	private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
	private static final UUID VISIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private final TravelPlanEstimateApiService estimates = mock(TravelPlanEstimateApiService.class);
	private final TravelBoundarySelectionService boundaries = mock(TravelBoundarySelectionService.class);
	private final RestaurantSearchService places = mock(RestaurantSearchService.class);
	private final RestaurantSearchCoordinationService service = new RestaurantSearchCoordinationService(
			estimates, boundaries, places, new RestaurantRecommendationService());

	@BeforeEach
	void setUp() {
		when(boundaries.verify("start-token", 7L, "KR-CITY"))
				.thenReturn(new TravelBoundarySelection("1", URI.create("https://place.map.kakao.com/1"),
						36, 127));
		when(boundaries.verify("end-token", 7L, "KR-CITY"))
				.thenReturn(new TravelBoundarySelection("2", URI.create("https://place.map.kakao.com/2"),
						36, 127.02));
		when(estimates.resolveAndEstimate(7L, estimate())).thenReturn(resolved());
	}

	@Test
	void recalculatesMealAndRanksProviderCandidatesWithoutChangingDraft() {
		when(places.search(anyLong(), any(UUID.class), any(RestaurantPlaceSearchRequest.class)))
				.thenReturn(new RestaurantPlaceSearchResult(List.of(
					new RestaurantPlaceSearchResult.Place("3", URI.create("https://place.map.kakao.com/3"),
							"식당", "테스트도 테스트시", new Coordinate(36, 127.01), "restaurant-token")),
					1, false));
		var response = service.search(7L, REQUEST_ID, request(MealType.LUNCH, VISIT_ID));
		assertThat(response.mealStartTime()).isEqualTo(LocalTime.NOON);
		assertThat(response.previousPlace().clientPlaceId()).isEqualTo(VISIT_ID);
		assertThat(response.nextPlace().kind()).isEqualTo("END_BOUNDARY");
		assertThat(response.referenceAttraction().clientPlaceId()).isEqualTo(VISIT_ID);
		assertThat(response.places()).singleElement().satisfies(place -> {
			assertThat(place.selectionToken()).isEqualTo("restaurant-token");
			assertThat(place.detourKilometers()).isNotNegative();
		});
		ArgumentCaptor<RestaurantPlaceSearchRequest> captured = ArgumentCaptor.forClass(
				RestaurantPlaceSearchRequest.class);
		verify(places).search(org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq(REQUEST_ID), captured.capture());
		assertThat(captured.getValue().center()).isEqualTo(new Coordinate(36, 127));
	}

	@Test
	void rejectsMissingMealAndForeignReferenceBeforeProviderSearch() {
		assertThatThrownBy(() -> service.search(7L, REQUEST_ID, request(MealType.DINNER, null)))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		assertThatThrownBy(() -> service.search(7L, REQUEST_ID,
				request(MealType.LUNCH, UUID.fromString("00000000-0000-0000-0000-000000000009"))))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(places, never()).search(anyLong(), any(UUID.class), any(RestaurantPlaceSearchRequest.class));
	}

	@Test
	void emptyProviderListReturnsReasonWithoutAutomaticChoice() {
		when(places.search(anyLong(), any(UUID.class), any(RestaurantPlaceSearchRequest.class)))
				.thenReturn(new RestaurantPlaceSearchResult(List.of(), 1, false));
		var response = service.search(7L, REQUEST_ID, request(MealType.LUNCH, null));
		assertThat(response.places()).isEmpty();
		assertThat(response.emptyReason()).isEqualTo("NO_CANDIDATES");
	}

	private RestaurantSearchApiRequest request(MealType type, UUID reference) {
		return new RestaurantSearchApiRequest(estimate(), DATE, type, "메뉴", reference,
				null, 1, 15);
	}

	private TravelPlanEstimateApiRequest estimate() {
		return new TravelPlanEstimateApiRequest("KR-CITY", TravelMode.CAR, DATE, DATE,
				"start-token", "end-token", List.of(new TravelPlanEstimateApiRequest.Day(DATE,
						LocalTime.of(9, 0), LocalTime.of(20, 0))),
				List.of(new TravelPlanEstimateApiRequest.Place(VISIT_ID, "visit-token", "방문",
						60, DATE, 1)), null, 15, List.of("메뉴"));
	}

	private ResolvedEstimate resolved() {
		var conditions = new TravelConditions(new TravelPeriod(DATE, DATE), TravelMode.CAR,
				List.of(new DailyActivityWindow(DATE, LocalTime.of(9, 0), LocalTime.of(20, 0))), 15);
		var visit = new EstimateVisit(VISIT_ID, new Coordinate(36, 127), "방문", 60, DATE, 1);
		var command = new EstimateCommand(7L, "KR-CITY", conditions, "start-token", "end-token",
				List.of(visit), null);
		var items = List.of(
				new EstimatedItem(1, EstimatedItem.Type.VISIT, VISIT_ID, "방문", null,
						DATE.atTime(10, 0), DATE.atTime(11, 0), null),
				new EstimatedItem(2, EstimatedItem.Type.MEAL, null, null, MealType.LUNCH,
						DATE.atTime(12, 0), DATE.atTime(13, 0), null));
		return new ResolvedEstimate(command, new EstimateResult(false,
				List.of(new EstimatedDay(DATE, DATE.atTime(14, 0), items))));
	}
}
