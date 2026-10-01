package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.HaversineTravelTimeEstimator;
import com.example.travel.route.algorithm.NearestNeighborRoute;
import com.example.travel.route.algorithm.RoutePoint;
import com.example.travel.route.algorithm.TravelTimePolicy;
import com.example.travel.route.algorithm.TwoOptRoute;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.MealSlotPolicy;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateResult;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.PlanCapacityDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Calculates a deterministic, request-scoped estimate without persistence or route HTTP calls. */
@Service
public class TravelPlanEstimateService {

	private static final String START_KEY = "!start";
	private static final String END_KEY = "!end";
	private static final List<String> CAPACITY_ADJUSTMENTS = List.of(
			"CHANGE_END_TIME", "CHANGE_STAY_MINUTES", "REMOVE_PLACE", "EXCLUDE_MEAL", "CHANGE_ORDER");

	private final TravelBoundarySelectionService boundarySelectionService;

	public TravelPlanEstimateService(TravelBoundarySelectionService boundarySelectionService) {
		this.boundarySelectionService = Objects.requireNonNull(boundarySelectionService);
	}

	public EstimateResult estimate(EstimateCommand command) {
		return calculate(command, Map.of(), true);
	}

	EstimateResult completionCandidate(EstimateCommand command,
			Map<LocalDate, Map<MealType, Coordinate>> selectedMeals) {
		Objects.requireNonNull(command, "command must not be null");
		if (command.visits().stream().anyMatch(visit -> visit.day() == null)) {
			throw validationFailed();
		}
		return calculate(command, selectedMeals, false);
	}

	private EstimateResult calculate(EstimateCommand command,
			Map<LocalDate, Map<MealType, Coordinate>> selectedMeals,
			boolean enforceCapacity) {
		Objects.requireNonNull(command, "command must not be null");
		Objects.requireNonNull(selectedMeals, "selectedMeals must not be null");
		validateAssignment(command);
		TravelBoundarySelection start = boundarySelectionService.verify(
				command.startBoundarySelectionToken(), command.userId(), command.regionId());
		TravelBoundarySelection end = boundarySelectionService.verify(
				command.endBoundarySelectionToken(), command.userId(), command.regionId());

		Coordinate startCoordinate = new Coordinate(start.latitude(), start.longitude());
		Coordinate endCoordinate = new Coordinate(end.latitude(), end.longitude());
		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(
				TravelTimePolicy.defaultFor(command.conditions().travelMode()));
		List<DailyActivityWindow> windows = command.conditions().days().stream()
				.sorted(Comparator.comparing(DailyActivityWindow::date)).toList();
		Map<LocalDate, List<EstimateVisit>> assigned = new LinkedHashMap<>();
		windows.forEach(window -> assigned.put(window.date(), new ArrayList<>()));
		boolean automatic = command.visits().stream().allMatch(visit -> visit.day() == null);

		if (automatic) {
			autoAssign(command, windows, assigned, startCoordinate, endCoordinate, estimator);
		} else {
			command.visits().forEach(visit -> assigned.get(visit.day()).add(visit));
			assigned.values().forEach(day -> day.sort(Comparator.comparingInt(EstimateVisit::order)));
		}

		List<EstimatedDay> result = new ArrayList<>();
		for (int index = 0; index < windows.size(); index++) {
			DailyActivityWindow window = windows.get(index);
			List<EstimateVisit> visits = automatic
					? optimizedVisits(assigned.get(window.date()), startFor(index, windows.size(), startCoordinate,
							command.hotelCoordinate()), endFor(index, windows.size(), endCoordinate,
							command.hotelCoordinate()))
					: assigned.get(window.date());
			DayPlan day = calculateDay(window, visits,
					startFor(index, windows.size(), startCoordinate, command.hotelCoordinate()),
					endFor(index, windows.size(), endCoordinate, command.hotelCoordinate()),
					estimator, command.conditions().mealTravelBufferMinutes(),
					selectedMeals.getOrDefault(window.date(), Map.of()));
			LocalDateTime limit = window.date().atTime(window.endTime());
			if (enforceCapacity && (!day.mealFeasible() || day.day().plannedEndTime().isAfter(limit))) {
				throw new ApiException(ErrorCode.PLAN_CAPACITY_EXCEEDED,
						new PlanCapacityDetails(window.date(), day.day().plannedEndTime().toLocalTime(),
								window.endTime(), Duration.between(limit, day.day().plannedEndTime()).toMinutes()),
						CAPACITY_ADJUSTMENTS, null);
			}
			result.add(day.day());
		}
		return new EstimateResult(false, result);
	}

	private static void validateAssignment(EstimateCommand command) {
		boolean automatic = command.visits().stream().allMatch(visit -> visit.day() == null);
		boolean manual = command.visits().stream().allMatch(visit -> visit.day() != null);
		if (!automatic && !manual) {
			throw validationFailed();
		}
		if (manual) {
			Map<LocalDate, List<Integer>> orders = new HashMap<>();
			for (EstimateVisit visit : command.visits()) {
				if (!command.conditions().period().contains(visit.day())) {
					throw validationFailed();
				}
				orders.computeIfAbsent(visit.day(), ignored -> new ArrayList<>()).add(visit.order());
			}
			for (List<Integer> dayOrders : orders.values()) {
				if (dayOrders.size() > 5) {
					throw validationFailed();
				}
				dayOrders.sort(Integer::compareTo);
				for (int index = 0; index < dayOrders.size(); index++) {
					if (dayOrders.get(index) != index + 1) {
						throw validationFailed();
					}
				}
			}
		}
	}

	private static void autoAssign(EstimateCommand command, List<DailyActivityWindow> windows,
			Map<LocalDate, List<EstimateVisit>> assigned, Coordinate start, Coordinate end,
			HaversineTravelTimeEstimator estimator) {
		List<EstimateVisit> remaining = new ArrayList<>(command.visits());
		remaining.sort(Comparator.comparing(visit -> visit.clientPlaceId().toString()));
		while (!remaining.isEmpty()) {
			Candidate best = null;
			for (int index = 0; index < windows.size(); index++) {
				DailyActivityWindow window = windows.get(index);
				List<EstimateVisit> selected = assigned.get(window.date());
				if (selected.size() >= 5) {
					continue;
				}
				Coordinate dayStart = startFor(index, windows.size(), start, command.hotelCoordinate());
				Coordinate dayEnd = endFor(index, windows.size(), end, command.hotelCoordinate());
				for (EstimateVisit visit : remaining) {
					List<EstimateVisit> trial = new ArrayList<>(selected);
					trial.add(visit);
					DayPlan plan = calculateDay(window, optimizedVisits(trial, dayStart, dayEnd),
						dayStart, dayEnd, estimator, command.conditions().mealTravelBufferMinutes(), Map.of());
					Candidate candidate = new Candidate(window.date(), visit, plan,
							Math.max(0, Duration.between(window.date().atTime(window.endTime()),
									plan.day().plannedEndTime()).toMinutes()),
							Duration.between(window.date().atTime(window.startTime()),
									plan.day().plannedEndTime()).toMinutes());
					if (best == null || candidate.compareTo(best) < 0) {
						best = candidate;
					}
				}
			}
			Objects.requireNonNull(best);
			assigned.get(best.date()).add(best.visit());
			remaining.remove(best.visit());
		}
	}

	private static List<EstimateVisit> optimizedVisits(List<EstimateVisit> visits, Coordinate start,
			Coordinate end) {
		Map<String, EstimateVisit> byKey = new HashMap<>();
		List<RoutePoint> points = new ArrayList<>();
		points.add(new RoutePoint(START_KEY, start));
		for (EstimateVisit visit : visits) {
			String key = visit.clientPlaceId().toString();
			byKey.put(key, visit);
			points.add(new RoutePoint(key, visit.coordinate()));
		}
		List<RoutePoint> route = new ArrayList<>(NearestNeighborRoute.order(points, Optional.of(START_KEY)));
		route.add(new RoutePoint(END_KEY, end));
		return TwoOptRoute.improve(route).stream().filter(point -> byKey.containsKey(point.stableKey()))
				.map(point -> byKey.get(point.stableKey())).toList();
	}

	private static Coordinate startFor(int index, int count, Coordinate start, Coordinate hotel) {
		return index == 0 ? start : hotel;
	}

	private static Coordinate endFor(int index, int count, Coordinate end, Coordinate hotel) {
		return index == count - 1 ? end : hotel;
	}

	private static DayPlan calculateDay(DailyActivityWindow window, List<EstimateVisit> visits,
			Coordinate start, Coordinate end, HaversineTravelTimeEstimator estimator, int buffer,
			Map<MealType, Coordinate> selectedMeals) {
		EnumMap<MealType, Placement> placements = new EnumMap<>(MealType.class);
		for (MealType mealType : MealType.values()) {
			if (MealSlotPolicy.createSlot(window, mealType).isEmpty()) {
				continue;
			}
			Placement best = chooseMeal(window, visits, start, end, estimator, buffer, placements,
					mealType, selectedMeals);
			if (best == null) {
				DayPlan bare = Objects.requireNonNull(render(window, visits, start, end,
						estimator, buffer, placements, selectedMeals));
				int mealBuffer = selectedMeals.containsKey(mealType) ? 0 : buffer;
				LocalDateTime earliestMealEnd = later(window.date().atTime(mealType.allowedStart()),
						window.date().atTime(window.startTime()).plusMinutes(mealBuffer))
						.plusMinutes(60L + mealBuffer);
				LocalDateTime projectedEnd = later(bare.day().plannedEndTime(), earliestMealEnd);
				projectedEnd = later(projectedEnd, window.date().atTime(window.endTime()).plusMinutes(1));
				return new DayPlan(new EstimatedDay(window.date(), projectedEnd, bare.day().items()),
						bare.travelMinutes(), false);
			}
			placements.put(mealType, best);
		}
		return Objects.requireNonNull(render(window, visits, start, end, estimator, buffer,
				placements, selectedMeals));
	}

	private static Placement chooseMeal(DailyActivityWindow window, List<EstimateVisit> visits,
			Coordinate start, Coordinate end, HaversineTravelTimeEstimator estimator, int buffer,
			EnumMap<MealType, Placement> selected, MealType mealType,
			Map<MealType, Coordinate> selectedMeals) {
		Placement best = null;
		long bestOverMinutes = Long.MAX_VALUE;
		long bestDifference = Long.MAX_VALUE;
		for (boolean included : List.of(true, false)) {
			int limit = included ? visits.size() : visits.size() + 1;
			for (int index = 0; index < limit; index++) {
				if (included && selectedMeals.containsKey(mealType)) {
					Coordinate visit = visits.get(index).coordinate();
					Coordinate restaurant = selectedMeals.get(mealType);
					int roundTrip = estimator.estimateMinutes(visit, restaurant)
							+ estimator.estimateMinutes(restaurant, visit);
					if (visits.get(index).stayMinutes() < 60 + roundTrip) continue;
				}
				Placement candidate = new Placement(included, index);
				EnumMap<MealType, Placement> trial = new EnumMap<>(selected);
				trial.put(mealType, candidate);
				DayPlan rendered = render(window, visits, start, end, estimator, buffer, trial,
						selectedMeals);
				if (rendered == null) {
					continue;
				}
				if (included && selectedMeals.containsKey(mealType)) {
					EstimateVisit visit = visits.get(index);
					EstimatedItem visitItem = rendered.day().items().stream()
							.filter(item -> visit.clientPlaceId().equals(item.clientPlaceId()))
							.findFirst().orElseThrow();
					Coordinate restaurant = selectedMeals.get(mealType);
					LocalDateTime earliest = later(visitItem.startTime().plusMinutes(
							estimator.estimateMinutes(visit.coordinate(), restaurant)),
							window.date().atTime(mealType.allowedStart()));
					LocalDateTime latest = earlier(visitItem.endTime().minusMinutes(60L
							+ estimator.estimateMinutes(restaurant, visit.coordinate())),
							window.date().atTime(mealType.allowedEnd()).minusMinutes(60));
					if (earliest.isAfter(latest)) continue;
				}
				EstimatedItem meal = rendered.day().items().stream()
						.filter(item -> item.type() == EstimatedItem.Type.MEAL && item.mealType() == mealType)
						.findFirst().orElseThrow();
				long difference = Math.abs(Duration.between(window.date().atTime(mealType.preferredStart()),
						meal.startTime()).toMinutes());
				long overMinutes = Math.max(0, Duration.between(window.date().atTime(window.endTime()),
						rendered.day().plannedEndTime()).toMinutes());
				if (overMinutes < bestOverMinutes
						|| (overMinutes == bestOverMinutes && difference < bestDifference)) {
					best = candidate;
					bestOverMinutes = overMinutes;
					bestDifference = difference;
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	private static DayPlan render(DailyActivityWindow window, List<EstimateVisit> visits,
			Coordinate start, Coordinate end, HaversineTravelTimeEstimator estimator, int buffer,
			Map<MealType, Placement> placements, Map<MealType, Coordinate> selectedMeals) {
		LocalDateTime cursor = window.date().atTime(window.startTime());
		Coordinate previous = start;
		List<EstimatedItem> items = new ArrayList<>();
		int travelMinutes = 0;
		for (int index = 0; index <= visits.size(); index++) {
			for (MealType mealType : MealType.values()) {
				Placement placement = placements.get(mealType);
				if (placement != null && !placement.included() && placement.index() == index) {
					int mealBuffer = selectedMeals.containsKey(mealType) ? 0 : buffer;
					LocalDateTime mealStart = regularMealStart(window, mealType, cursor, mealBuffer);
					if (mealStart == null) {
						return null;
					}
					LocalDateTime mealEnd = mealStart.plusMinutes(60);
					items.add(new EstimatedItem(0, EstimatedItem.Type.MEAL, null, null,
							mealType, mealStart, mealEnd, null));
					cursor = mealEnd.plusMinutes(mealBuffer);
				}
			}
			Coordinate next = index == visits.size() ? end : visits.get(index).coordinate();
			int moveMinutes = estimator.estimateMinutes(previous, next);
			items.add(new EstimatedItem(0, EstimatedItem.Type.MOVE, null, null, null,
					cursor, cursor.plusMinutes(moveMinutes), moveMinutes));
			cursor = cursor.plusMinutes(moveMinutes);
			travelMinutes += moveMinutes;
			previous = next;
			if (index == visits.size()) {
				continue;
			}
			EstimateVisit visit = visits.get(index);
			LocalDateTime visitStart = cursor;
			LocalDateTime visitEnd = cursor.plusMinutes(visit.stayMinutes());
			items.add(new EstimatedItem(0, EstimatedItem.Type.VISIT, visit.clientPlaceId(),
					visit.displayName(), null, visitStart, visitEnd, null));
			LocalDateTime priorMealEnd = null;
			for (MealType mealType : MealType.values()) {
				Placement placement = placements.get(mealType);
				if (placement != null && placement.included() && placement.index() == index) {
					LocalDateTime mealStart = includedMealStart(window, mealType, visitStart,
							visitEnd, buffer, priorMealEnd);
					if (mealStart == null) {
						return null;
					}
					priorMealEnd = mealStart.plusMinutes(60);
					items.add(new EstimatedItem(0, EstimatedItem.Type.MEAL, null, null,
							mealType, mealStart, priorMealEnd, null));
				}
			}
			cursor = visitEnd;
		}
		List<EstimatedItem> ordered = new ArrayList<>();
		for (int index = 0; index < items.size(); index++) {
			EstimatedItem item = items.get(index);
			ordered.add(new EstimatedItem(index + 1, item.type(), item.clientPlaceId(),
					item.displayName(), item.mealType(), item.startTime(), item.endTime(),
					item.estimatedMinutes()));
		}
		return new DayPlan(new EstimatedDay(window.date(), cursor, ordered), travelMinutes, true);
	}

	private static LocalDateTime regularMealStart(DailyActivityWindow window, MealType type,
			LocalDateTime cursor, int buffer) {
		LocalDateTime earliest = later(cursor.plusMinutes(buffer),
				window.date().atTime(type.allowedStart()));
		LocalDateTime latest = window.date().atTime(type.allowedEnd()).minusMinutes(60);
		if (earliest.isAfter(latest)) {
			return null;
		}
		return clamp(window.date().atTime(type.preferredStart()), earliest, latest);
	}

	private static LocalDateTime includedMealStart(DailyActivityWindow window, MealType type,
			LocalDateTime visitStart, LocalDateTime visitEnd, int buffer,
			LocalDateTime priorMealEnd) {
		LocalDateTime earliest = later(visitStart.plusMinutes(buffer),
				window.date().atTime(type.allowedStart()));
		if (priorMealEnd != null) {
			earliest = later(earliest, priorMealEnd.plusMinutes(2L * buffer));
		}
		LocalDateTime latest = earlier(visitEnd.minusMinutes(buffer + 60),
				window.date().atTime(type.allowedEnd()).minusMinutes(60));
		return earliest.isAfter(latest) ? null
				: clamp(window.date().atTime(type.preferredStart()), earliest, latest);
	}

	private static LocalDateTime clamp(LocalDateTime value, LocalDateTime min, LocalDateTime max) {
		return earlier(later(value, min), max);
	}

	private static LocalDateTime later(LocalDateTime left, LocalDateTime right) {
		return left.isAfter(right) ? left : right;
	}

	private static LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
		return left.isBefore(right) ? left : right;
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}

	private record Placement(boolean included, int index) { }

	private record DayPlan(EstimatedDay day, int travelMinutes, boolean mealFeasible) { }

	private record Candidate(LocalDate date, EstimateVisit visit, DayPlan plan,
			long overMinutes, long elapsedMinutes) implements Comparable<Candidate> {
		@Override
		public int compareTo(Candidate other) {
			int comparison = Long.compare(overMinutes, other.overMinutes);
			if (comparison == 0) comparison = Long.compare(elapsedMinutes, other.elapsedMinutes);
			if (comparison == 0) comparison = Integer.compare(plan.travelMinutes(), other.plan.travelMinutes());
			if (comparison == 0) comparison = date.compareTo(other.date);
			if (comparison == 0) comparison = visit.clientPlaceId().compareTo(other.visit.clientPlaceId());
			return comparison;
		}
	}
}
