package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.RestaurantPlaceSearchRequest;
import com.example.travel.place.dto.RestaurantPlaceSearchResult;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.RestaurantSearchService;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.recommendation.dto.RestaurantCandidate;
import com.example.travel.recommendation.dto.RestaurantRecommendationRequest;
import com.example.travel.recommendation.dto.RestaurantRecommendationResult;
import com.example.travel.recommendation.dto.ScoredRestaurant;
import com.example.travel.recommendation.service.RestaurantRecommendationService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.travelplan.domain.MealSlot;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.ResolvedEstimate;
import com.example.travel.travelplan.dto.RestaurantSearchApiRequest;
import com.example.travel.travelplan.dto.RestaurantSearchApiResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Recalculates a confirmed draft and coordinates place search with recommendation. */
@Service
public class RestaurantSearchCoordinationService {
	private final TravelPlanEstimateApiService estimateApiService;
	private final TravelBoundarySelectionService boundaryService;
	private final RestaurantSearchService placeSearchService;
	private final RestaurantRecommendationService recommendationService;

	public RestaurantSearchCoordinationService(TravelPlanEstimateApiService estimateApiService,
			TravelBoundarySelectionService boundaryService, RestaurantSearchService placeSearchService,
			RestaurantRecommendationService recommendationService) {
		this.estimateApiService = estimateApiService;
		this.boundaryService = boundaryService;
		this.placeSearchService = placeSearchService;
		this.recommendationService = recommendationService;
	}

	public RestaurantSearchApiResponse search(long userId, UUID requestId,
			RestaurantSearchApiRequest request) {
		validate(userId, requestId, request);
		ResolvedEstimate resolved = estimateApiService.resolveAndEstimate(userId, request.estimate());
		EstimateCommand command = resolved.command();
		EstimatedDay day = resolved.result().days().stream()
				.filter(value -> value.date().equals(request.mealDate()))
				.findFirst().orElseThrow(RestaurantSearchCoordinationService::validationFailed);
		EstimatedItem meal = day.items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MEAL
						&& item.mealType() == request.mealType())
				.findFirst().orElseThrow(RestaurantSearchCoordinationService::validationFailed);
		Map<UUID, EstimateVisit> visits = new HashMap<>();
		command.visits().forEach(visit -> visits.put(visit.clientPlaceId(), visit));
		TravelBoundarySelection startSelection = boundaryService.verify(
				command.startBoundarySelectionToken(), userId, command.regionId());
		TravelBoundarySelection endSelection = boundaryService.verify(
				command.endBoundarySelectionToken(), userId, command.regionId());
		int dayIndex = command.conditions().period().dates().indexOf(request.mealDate());
		int lastDay = command.conditions().period().dayCount() - 1;
		DailyActivityWindow window = command.conditions().days().stream()
				.filter(value -> value.date().equals(request.mealDate()))
				.findFirst().orElseThrow(RestaurantSearchCoordinationService::validationFailed);
		ReferencePoint dayStart = dayIndex == 0
				? new ReferencePoint("START_BOUNDARY", null, coordinate(startSelection),
						request.mealDate().atTime(window.startTime()))
				: new ReferencePoint("HOTEL", null, command.hotelCoordinate(),
						request.mealDate().atTime(window.startTime()));
		ReferencePoint dayEnd = dayIndex == lastDay
				? new ReferencePoint("END_BOUNDARY", null, coordinate(endSelection),
						request.mealDate().atTime(window.endTime()))
				: new ReferencePoint("HOTEL", null, command.hotelCoordinate(),
						request.mealDate().atTime(window.endTime()));
		References references = references(day, meal, visits, dayStart, dayEnd);
		ReferencePoint attraction = referenceAttraction(request, visits);
		Coordinate center = attraction != null ? attraction.coordinate()
				: new Coordinate((references.previous().coordinate().latitude()
						+ references.next().coordinate().latitude()) / 2,
						(references.previous().coordinate().longitude()
								+ references.next().coordinate().longitude()) / 2);
		RestaurantSearchApiRequest.Bounds bounds = request.bounds();
		RestaurantPlaceSearchResult found = placeSearchService.search(userId, requestId,
				new RestaurantPlaceSearchRequest(command.regionId(), request.menuQuery().trim(),
						bounds == null ? center : null,
						bounds == null ? null : new RestaurantPlaceSearchRequest.Bounds(
								bounds.minLatitude(), bounds.minLongitude(),
								bounds.maxLatitude(), bounds.maxLongitude()), request.page(), request.size()));
		RestaurantRecommendationResult ranked = recommendationService.recommend(
				new RestaurantRecommendationRequest(new MealSlot(request.mealDate(), request.mealType(),
						meal.startTime().toLocalTime(), meal.endTime().toLocalTime()),
						references.previous().time(), references.next().time(),
						references.previous().coordinate(), references.next().coordinate(),
						command.conditions().travelMode(),
						RestaurantRecommendationRequest.LookupOutcome.SUCCESS,
						found.places().stream().map(place -> new RestaurantCandidate(
								place.kakaoPlaceId(), place.coordinate())).toList()));
		Map<String, RestaurantPlaceSearchResult.Place> byId = new HashMap<>();
		found.places().forEach(place -> byId.put(place.kakaoPlaceId(), place));
		List<RestaurantSearchApiResponse.Place> places = ranked.restaurants().stream()
				.map(item -> toResponsePlace(item, byId.get(item.candidate().placeId()))).toList();
		return new RestaurantSearchApiResponse(request.mealDate(), meal.startTime().toLocalTime(),
				meal.endTime().toLocalTime(), toResponseReference(references.previous()),
				toResponseReference(references.next()),
				attraction == null ? null : toResponseReference(attraction), places,
				places.isEmpty() ? "NO_CANDIDATES" : null, found.page(), found.hasNext());
	}

	private static References references(EstimatedDay day, EstimatedItem meal,
			Map<UUID, EstimateVisit> visits, ReferencePoint dayStart, ReferencePoint dayEnd) {
		List<EstimatedItem> items = day.items();
		int mealIndex = items.indexOf(meal);
		ReferencePoint previous = dayStart;
		for (int index = 0; index < mealIndex; index++) {
			EstimatedItem item = items.get(index);
			if (item.type() == EstimatedItem.Type.VISIT) {
				previous = visitReference(item, visits, item.endTime());
			}
		}
		if (previous.clientPlaceId() != null) {
			UUID previousId = previous.clientPlaceId();
			EstimatedItem visit = items.stream().filter(item -> item.type() == EstimatedItem.Type.VISIT
					&& item.clientPlaceId().equals(previousId)
					&& !item.startTime().isAfter(meal.startTime())
					&& !item.endTime().isBefore(meal.endTime())).findFirst().orElse(null);
			if (visit != null) {
				return new References(visitReference(visit, visits, visit.startTime()),
						visitReference(visit, visits, visit.endTime()));
			}
		}
		ReferencePoint next = dayEnd;
		for (int index = mealIndex + 1; index < items.size(); index++) {
			EstimatedItem item = items.get(index);
			if (item.type() == EstimatedItem.Type.VISIT) {
				next = visitReference(item, visits, item.startTime());
				break;
			}
		}
		return new References(previous, next);
	}

	private static ReferencePoint visitReference(EstimatedItem item,
			Map<UUID, EstimateVisit> visits, LocalDateTime time) {
		EstimateVisit visit = visits.get(item.clientPlaceId());
		if (visit == null) throw validationFailed();
		return new ReferencePoint("ATTRACTION", item.clientPlaceId(), visit.coordinate(), time);
	}

	private static ReferencePoint referenceAttraction(RestaurantSearchApiRequest request,
			Map<UUID, EstimateVisit> visits) {
		if (request.referenceAttractionClientPlaceId() == null) return null;
		EstimateVisit visit = visits.get(request.referenceAttractionClientPlaceId());
		if (visit == null || !request.mealDate().equals(visit.day())) throw validationFailed();
		return new ReferencePoint("ATTRACTION", visit.clientPlaceId(), visit.coordinate(), null);
	}

	private static Coordinate coordinate(TravelBoundarySelection place) {
		return new Coordinate(place.latitude(), place.longitude());
	}

	private static RestaurantSearchApiResponse.Reference toResponseReference(ReferencePoint point) {
		return new RestaurantSearchApiResponse.Reference(point.kind(), point.clientPlaceId(),
				point.coordinate().latitude(), point.coordinate().longitude());
	}

	private static RestaurantSearchApiResponse.Place toResponsePlace(ScoredRestaurant item,
			RestaurantPlaceSearchResult.Place place) {
		return new RestaurantSearchApiResponse.Place(place.kakaoPlaceId(), place.placeUrl(),
				place.providerDisplayName(), place.address(), place.coordinate().latitude(),
				place.coordinate().longitude(), place.selectionToken(), item.detourKilometers(),
				item.estimatedDetourMinutes());
	}

	private static void validate(long userId, UUID requestId, RestaurantSearchApiRequest request) {
		if (userId <= 0 || requestId == null || request == null || request.estimate() == null
				|| request.mealDate() == null || request.mealType() == null
				|| request.menuQuery() == null || request.menuQuery().isBlank()
				|| request.menuQuery().trim().length() > 50
				|| request.page() < 1 || request.page() > 45
				|| request.size() < 1 || request.size() > 15
				|| request.estimate().places() == null
				|| request.estimate().places().stream().anyMatch(place -> place == null
						|| place.day() == null || place.order() == null)) {
			throw validationFailed();
		}
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}

	private record ReferencePoint(String kind, UUID clientPlaceId,
			Coordinate coordinate, LocalDateTime time) { }
	private record References(ReferencePoint previous, ReferencePoint next) { }
}
