package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.client.RouteClientException;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.route.service.RouteObservationEvent;
import com.example.travel.route.service.RouteSegmentTravelTime;
import com.example.travel.route.service.RouteVerificationResult;
import com.example.travel.route.service.RouteVerificationService;
import com.example.travel.route.service.RouteWarning;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.MealSlotPolicy;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.PlanCapacityDetails;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Recalculates a confirmed, request-scoped itinerary before any persistence transaction. */
public class TravelPlanCompletionCalculationService {

	private static final List<String> CAPACITY_ADJUSTMENTS = List.of(
			"CHANGE_END_TIME", "CHANGE_STAY_MINUTES", "REMOVE_PLACE", "EXCLUDE_MEAL", "CHANGE_ORDER");

	private final TravelPlanEstimateService estimateService;
	private final TravelBoundarySelectionService boundarySelectionService;
	private final RouteVerificationService routeVerificationService;

	public TravelPlanCompletionCalculationService(TravelPlanEstimateService estimateService,
			TravelBoundarySelectionService boundarySelectionService,
			RouteVerificationService routeVerificationService) {
		this.estimateService = Objects.requireNonNull(estimateService);
		this.boundarySelectionService = Objects.requireNonNull(boundarySelectionService);
		this.routeVerificationService = Objects.requireNonNull(routeVerificationService);
	}

	public CalculationResult calculate(EstimateCommand command,
			Map<LocalDate, Map<MealType, Coordinate>> selectedRestaurants) {
		Objects.requireNonNull(command, "command must not be null");
		Objects.requireNonNull(selectedRestaurants, "selectedRestaurants must not be null");
		selectedRestaurants.forEach((date, meals) -> {
			if (date == null || meals == null || meals.keySet().stream().anyMatch(Objects::isNull)
					|| meals.values().stream().anyMatch(Objects::isNull)) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED);
			}
		});
		List<EstimatedDay> candidate = estimateService.completionCandidate(command, selectedRestaurants).days();
		Map<UUID, EstimateVisit> visits = new HashMap<>();
		command.visits().forEach(visit -> visits.put(visit.clientPlaceId(), visit));
		TravelBoundarySelection start = boundarySelectionService.verify(
				command.startBoundarySelectionToken(), command.userId(), command.regionId());
		TravelBoundarySelection end = boundarySelectionService.verify(
				command.endBoundarySelectionToken(), command.userId(), command.regionId());
		Coordinate startCoordinate = new Coordinate(start.latitude(), start.longitude());
		Coordinate endCoordinate = new Coordinate(end.latitude(), end.longitude());
		List<DailyActivityWindow> windows = command.conditions().days().stream()
				.sorted(Comparator.comparing(DailyActivityWindow::date)).toList();
		List<DayRoute> dayRoutes = new ArrayList<>();
		List<RouteSegment> segments = new ArrayList<>();
		for (int index = 0; index < windows.size(); index++) {
			DailyActivityWindow window = windows.get(index);
			Map<MealType, Coordinate> meals = selectedRestaurants.getOrDefault(window.date(), Map.of());
			Set<MealType> expected = new HashSet<>();
			for (MealType type : MealType.values()) {
				if (MealSlotPolicy.createSlot(window, type).isPresent()) expected.add(type);
			}
			if (!expected.containsAll(meals.keySet())) throw new ApiException(ErrorCode.VALIDATION_FAILED);
			List<Event> events = events(candidate.get(index), visits, meals);
			Set<MealType> actual = new HashSet<>();
			events.stream().filter(event -> event.item().type() == EstimatedItem.Type.MEAL)
					.forEach(event -> actual.add(event.item().mealType()));
			if (!actual.equals(expected)) throw capacity(window, window.date().atTime(window.endTime()).plusMinutes(1));
			Coordinate dayStart = index == 0 ? startCoordinate : command.hotelCoordinate();
			Coordinate dayEnd = index == windows.size() - 1 ? endCoordinate : command.hotelCoordinate();
			Coordinate previous = dayStart;
			int firstSegment = segments.size();
			for (Event event : events) {
				if (event.coordinate() != null) {
					segments.add(segment(previous, event.coordinate()));
					if (event.included()) {
						segments.add(segment(event.coordinate(), previous));
					} else {
						previous = event.coordinate();
					}
				}
			}
			segments.add(segment(previous, dayEnd));
			dayRoutes.add(new DayRoute(window, events, firstSegment, segments.size()));
		}
		if (selectedRestaurants.keySet().stream().anyMatch(date -> windows.stream()
				.noneMatch(window -> window.date().equals(date)))) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED);
		}
		RouteVerificationResult verified;
		try {
			verified = routeVerificationService.verify(command.userId(),
					command.conditions().travelMode(), segments);
		} catch (RouteClientException exception) {
			throw new ApiException(ErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
		}
		List<RouteSegmentTravelTime> times = verified.travelTimes().segmentTravelTimes();
		if (times.size() != segments.size()) throw new IllegalStateException("route segment count changed");
		List<EstimatedDay> days = new ArrayList<>();
		for (DayRoute route : dayRoutes) {
			days.add(render(route, times, command.conditions().mealTravelBufferMinutes()));
		}
		return new CalculationResult(days, verified.warnings(), verified.observationEvent());
	}

	private static List<Event> events(EstimatedDay candidate, Map<UUID, EstimateVisit> visits,
			Map<MealType, Coordinate> restaurants) {
		List<Event> events = new ArrayList<>();
		EstimatedItem lastVisit = null;
		for (EstimatedItem item : candidate.items()) {
			if (item.type() == EstimatedItem.Type.VISIT) {
				lastVisit = item;
				events.add(new Event(item, visits.get(item.clientPlaceId()).coordinate(), false));
			} else if (item.type() == EstimatedItem.Type.MEAL) {
				Coordinate restaurant = restaurants.get(item.mealType());
				boolean included = lastVisit != null
						&& !item.startTime().isBefore(lastVisit.startTime())
						&& !item.endTime().isAfter(lastVisit.endTime());
				events.add(new Event(item, restaurant, included));
			}
		}
		return events;
	}

	private static EstimatedDay render(DayRoute route, List<RouteSegmentTravelTime> times, int buffer) {
		DailyActivityWindow window = route.window();
		LocalDateTime cursor = window.date().atTime(window.startTime());
		List<EstimatedItem> items = new ArrayList<>();
		int segmentIndex = route.firstSegment();
		LocalDateTime visitStart = null;
		LocalDateTime visitEnd = null;
		LocalDateTime priorIncludedMealEnd = null;
		for (Event event : route.events()) {
			EstimatedItem item = event.item();
			if (event.coordinate() != null && !event.included()) {
				int minutes = minutes(times.get(segmentIndex++));
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MOVE,
						null, null, null, cursor, cursor.plusMinutes(minutes), minutes));
				cursor = cursor.plusMinutes(minutes);
			}
			if (item.type() == EstimatedItem.Type.VISIT) {
				visitStart = cursor;
				visitEnd = cursor.plus(Duration.between(item.startTime(), item.endTime()));
				priorIncludedMealEnd = null;
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.VISIT,
						item.clientPlaceId(), item.displayName(), null, visitStart, visitEnd, null));
				cursor = visitEnd;
			} else if (event.included() && event.coordinate() != null) {
				int outward = minutes(times.get(segmentIndex++));
				int back = minutes(times.get(segmentIndex++));
				LocalDateTime earliest = max(visitStart.plusMinutes(outward),
						window.date().atTime(item.mealType().allowedStart()));
				if (priorIncludedMealEnd != null) earliest = max(earliest, priorIncludedMealEnd);
				LocalDateTime latest = min(visitEnd.minusMinutes(back + 60L),
						window.date().atTime(item.mealType().allowedEnd()).minusMinutes(60));
				if (earliest.isAfter(latest)) throw capacity(window, max(cursor,
						window.date().atTime(window.endTime()).plusMinutes(1)));
				LocalDateTime mealStart = min(max(window.date().atTime(item.mealType().preferredStart()), earliest), latest);
				LocalDateTime outwardStart = mealStart.minusMinutes(outward);
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MOVE,
						null, null, null, outwardStart, mealStart, outward));
				LocalDateTime mealEnd = mealStart.plusMinutes(60);
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MEAL,
						null, null, item.mealType(), mealStart, mealEnd, null));
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MOVE,
						null, null, null, mealEnd, mealEnd.plusMinutes(back), back));
				priorIncludedMealEnd = mealEnd.plusMinutes(back);
			} else if (event.included()) {
				LocalDateTime earliest = max(visitStart.plusMinutes(buffer),
						window.date().atTime(item.mealType().allowedStart()));
				if (priorIncludedMealEnd != null) earliest = max(earliest,
						priorIncludedMealEnd.plusMinutes(2L * buffer));
				LocalDateTime latest = min(visitEnd.minusMinutes(buffer + 60L),
						window.date().atTime(item.mealType().allowedEnd()).minusMinutes(60));
				if (earliest.isAfter(latest)) throw capacity(window, max(cursor,
						window.date().atTime(window.endTime()).plusMinutes(1)));
				LocalDateTime mealStart = min(max(window.date().atTime(item.mealType().preferredStart()), earliest), latest);
				priorIncludedMealEnd = mealStart.plusMinutes(60);
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MEAL,
						null, null, item.mealType(), mealStart, priorIncludedMealEnd, null));
			} else {
				LocalDateTime earliest = max(cursor.plusMinutes(event.coordinate() == null ? buffer : 0),
						window.date().atTime(item.mealType().allowedStart()));
				LocalDateTime latest = window.date().atTime(item.mealType().allowedEnd()).minusMinutes(60);
				if (earliest.isAfter(latest)) throw capacity(window, max(earliest.plusMinutes(60),
						window.date().atTime(window.endTime()).plusMinutes(1)));
				LocalDateTime mealStart = min(max(window.date().atTime(item.mealType().preferredStart()), earliest), latest);
				LocalDateTime mealEnd = mealStart.plusMinutes(60);
				items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MEAL,
						null, null, item.mealType(), mealStart, mealEnd, null));
				cursor = mealEnd.plusMinutes(event.coordinate() == null ? buffer : 0);
			}
		}
		int minutes = minutes(times.get(segmentIndex++));
		items.add(new EstimatedItem(items.size() + 1, EstimatedItem.Type.MOVE,
				null, null, null, cursor, cursor.plusMinutes(minutes), minutes));
		cursor = cursor.plusMinutes(minutes);
		if (segmentIndex != route.endSegment()) throw new IllegalStateException("day segment count changed");
		if (cursor.isAfter(window.date().atTime(window.endTime()))) throw capacity(window, cursor);
		return new EstimatedDay(window.date(), cursor, items);
	}

	private static int minutes(RouteSegmentTravelTime time) {
		return switch (time) {
			case RouteSegmentTravelTime.Found found -> found.estimatedMinutes();
			case RouteSegmentTravelTime.NotFound ignored -> throw new ApiException(ErrorCode.ROUTE_NOT_FOUND);
		};
	}

	private static RouteSegment segment(Coordinate from, Coordinate to) {
		return new RouteSegment(new RouteSegment.Endpoint(from.latitude(), from.longitude()),
				new RouteSegment.Endpoint(to.latitude(), to.longitude()));
	}

	private static LocalDateTime max(LocalDateTime left, LocalDateTime right) {
		return left.isAfter(right) ? left : right;
	}

	private static LocalDateTime min(LocalDateTime left, LocalDateTime right) {
		return left.isBefore(right) ? left : right;
	}

	private static ApiException capacity(DailyActivityWindow window, LocalDateTime plannedEnd) {
		LocalDateTime allowedEnd = window.date().atTime(window.endTime());
		return new ApiException(ErrorCode.PLAN_CAPACITY_EXCEEDED,
				new PlanCapacityDetails(window.date(), plannedEnd.toLocalTime(), window.endTime(),
						Duration.between(allowedEnd, plannedEnd).toMinutes()),
				CAPACITY_ADJUSTMENTS, null);
	}

	public record CalculationResult(List<EstimatedDay> days, List<RouteWarning> warnings,
			RouteObservationEvent observationEvent) {
		public CalculationResult {
			days = List.copyOf(days);
			warnings = List.copyOf(warnings);
		}
	}

	private record Event(EstimatedItem item, Coordinate coordinate, boolean included) { }
	private record DayRoute(DailyActivityWindow window, List<Event> events,
			int firstSegment, int endSegment) { }
}
