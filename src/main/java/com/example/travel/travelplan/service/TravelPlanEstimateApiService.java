package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.service.PlanningPlaceSelectionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.domain.TravelPlanInputPolicy;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateResult;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.ResolvedEstimate;
import com.example.travel.travelplan.dto.TravelConditions;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Resolves request tokens and maps the estimate without retaining provider coordinates. */
@Service
public class TravelPlanEstimateApiService {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final PlanningPlaceSelectionService placeSelectionService;
	private final TravelPlanEstimateService estimateService;

	public TravelPlanEstimateApiService(PlanningPlaceSelectionService placeSelectionService,
			TravelPlanEstimateService estimateService) {
		this.placeSelectionService = Objects.requireNonNull(placeSelectionService);
		this.estimateService = Objects.requireNonNull(estimateService);
	}

	public TravelPlanEstimateApiResponse estimate(long userId, TravelPlanEstimateApiRequest request) {
		return toResponse(resolveAndEstimate(userId, request).result());
	}

	public ResolvedEstimate resolveAndEstimate(long userId, TravelPlanEstimateApiRequest request) {
		TravelConditions conditions = validateRequest(userId, request);
		List<EstimateVisit> visits = new ArrayList<>(request.places().size());
		for (TravelPlanEstimateApiRequest.Place place : request.places()) {
			PlanningPlaceSelection selected = placeSelectionService.verifyAttraction(
					place.selectionToken(), userId, request.regionId());
			visits.add(new EstimateVisit(place.clientPlaceId(), coordinate(selected),
					place.displayName(), place.stayMinutes(), place.day(), place.order()));
		}
		Coordinate hotel = null;
		if (conditions.period().dayCount() > 1) {
			hotel = coordinate(placeSelectionService.verifyHotel(
					request.hotelSelectionToken(), userId, request.regionId()));
		}
		EstimateCommand command = new EstimateCommand(userId, request.regionId(), conditions,
				request.startBoundarySelectionToken(), request.endBoundarySelectionToken(), visits, hotel);
		return new ResolvedEstimate(command, estimateService.estimate(command));
	}

	private static TravelConditions validateRequest(long userId, TravelPlanEstimateApiRequest request) {
		if (userId <= 0 || request == null || request.regionId() == null || request.regionId().isBlank()
				|| request.travelMode() == null || request.days() == null || request.places() == null
				|| request.startBoundarySelectionToken() == null
				|| request.startBoundarySelectionToken().isBlank()
				|| request.endBoundarySelectionToken() == null
				|| request.endBoundarySelectionToken().isBlank()) {
			throw validationFailed();
		}
		try {
			TravelPeriod period = new TravelPeriod(request.startDate(), request.endDate());
			int buffer = request.mealTravelBufferMinutes() == null
					? TravelPlanInputPolicy.DEFAULT_MEAL_TRAVEL_BUFFER_MINUTES
					: request.mealTravelBufferMinutes();
			List<DailyActivityWindow> days = request.days().stream()
					.map(day -> new DailyActivityWindow(day.date(), day.activityStartTime(),
							day.activityEndTime())).toList();
			TravelConditions conditions = new TravelConditions(period, request.travelMode(), days, buffer);
			validatePlaces(request.places(), period);
			validateFoods(request.foods());
			if ((period.dayCount() == 1 && request.hotelSelectionToken() != null)
					|| (period.dayCount() > 1 && (request.hotelSelectionToken() == null
						|| request.hotelSelectionToken().isBlank()))) {
				throw validationFailed();
			}
			return conditions;
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw validationFailed();
		}
	}

	private static void validatePlaces(List<TravelPlanEstimateApiRequest.Place> places,
			TravelPeriod period) {
		if (places.size() > period.dayCount() * 5) {
			throw validationFailed();
		}
		Set<UUID> ids = new HashSet<>();
		Map<LocalDate, List<Integer>> manualOrders = new HashMap<>();
		int assigned = 0;
		for (TravelPlanEstimateApiRequest.Place place : places) {
			if (place == null || place.clientPlaceId() == null || !ids.add(place.clientPlaceId())
					|| place.selectionToken() == null || place.selectionToken().isBlank()
					|| place.displayName() == null || place.displayName().trim().isEmpty()
					|| place.displayName().trim().length() > 50 || place.stayMinutes() == null
					|| (place.day() == null) != (place.order() == null)) {
				throw validationFailed();
			}
			TravelPlanInputPolicy.validateStayMinutes(place.stayMinutes());
			if (place.day() != null) {
				if (!period.contains(place.day()) || place.order() < 1) {
					throw validationFailed();
				}
				assigned++;
				manualOrders.computeIfAbsent(place.day(), ignored -> new ArrayList<>()).add(place.order());
			}
		}
		if (assigned != 0 && assigned != places.size()) {
			throw validationFailed();
		}
		for (List<Integer> orders : manualOrders.values()) {
			if (orders.size() > 5) {
				throw validationFailed();
			}
			orders.sort(Integer::compareTo);
			for (int index = 0; index < orders.size(); index++) {
				if (orders.get(index) != index + 1) {
					throw validationFailed();
				}
			}
		}
	}

	private static void validateFoods(List<String> foods) {
		if (foods == null || foods.isEmpty() || foods.size() > 5) {
			throw validationFailed();
		}
		Set<String> unique = new HashSet<>();
		for (String food : foods) {
			if (food == null || food.trim().isEmpty() || food.trim().length() > 50
					|| !unique.add(food.trim())) {
				throw validationFailed();
			}
		}
	}

	private static Coordinate coordinate(PlanningPlaceSelection selection) {
		return new Coordinate(selection.latitude(), selection.longitude());
	}

	private static TravelPlanEstimateApiResponse toResponse(EstimateResult result) {
		List<TravelPlanEstimateApiResponse.Day> days = result.days().stream()
				.map(TravelPlanEstimateApiService::toDay).toList();
		return new TravelPlanEstimateApiResponse(result.routeVerified(), days);
	}

	private static TravelPlanEstimateApiResponse.Day toDay(EstimatedDay day) {
		List<TravelPlanEstimateApiResponse.Item> items = day.items().stream().map(item ->
				new TravelPlanEstimateApiResponse.Item(item.clientPlaceId(), item.order(), item.type(),
						item.displayName(), item.startTime().format(TIME_FORMAT),
						item.endTime().format(TIME_FORMAT), item.estimatedMinutes())).toList();
		return new TravelPlanEstimateApiResponse.Day(day.date(), items);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}
}
