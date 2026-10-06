package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.service.PlanningPlaceSelectionService;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.travelplan.domain.MealSlotPolicy;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.domain.PlanPlace;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.domain.TravelPlanDay;
import com.example.travel.travelplan.domain.TravelPlanItem;
import com.example.travel.travelplan.dto.CompletedPlanSaveCommand;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.TravelPlanCreateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanCreateApiResponse;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.service.RequestExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TravelPlanCreateApiService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TravelPlanEstimateApiService estimates;
    private final PlanningPlaceSelectionService selections;
    private final PlaceSearchRegionService regions;
    private final TravelPlanCompletionCalculationService calculations;
    private final TravelPlanCompletionSaveService saves;
    private final TransactionTemplate transactions;
    private final RequestExecutionService executions;
    private final TravelPlanRepository plans;
    private final TravelPlanDayRepository days;
    private final TravelPlanItemRepository items;
    private final PlanPlaceRepository places;

    public TravelPlanCreateApiService(TravelPlanEstimateApiService estimates,
            PlanningPlaceSelectionService selections, PlaceSearchRegionService regions,
            TravelPlanCompletionCalculationService calculations, TravelPlanCompletionSaveService saves,
            TransactionTemplate transactions, RequestExecutionService executions, TravelPlanRepository plans,
            TravelPlanDayRepository days, TravelPlanItemRepository items, PlanPlaceRepository places) {
        this.estimates = estimates;
        this.selections = selections;
        this.regions = regions;
        this.calculations = calculations;
        this.saves = saves;
        this.transactions = transactions;
        this.executions = executions;
        this.plans = plans;
        this.days = days;
        this.items = items;
        this.places = places;
    }

    public TravelPlanCreateApiResponse create(long userId, UUID requestId, TravelPlanCreateApiRequest request) {
        if (request == null || requestId == null || !text(request.title(), 100)) fail();
        RequestExecutionLease lease = executions.startTravelPlanCreation(userId, requestId);
        try {
            EstimateCommand command = estimates.resolve(userId, request.estimate());
            if (request.places().stream().anyMatch(place -> place.day() == null)) fail();
            PlaceSearchRegion region = regions.findById(request.regionId())
                    .filter(PlaceSearchRegion::selectable).orElseThrow(TravelPlanCreateApiService::invalid);
            if ((command.conditions().period().dayCount() == 1 && request.hotelDisplayName() != null)
                    || (request.hotelDisplayName() != null && !text(request.hotelDisplayName(), 50))) fail();

            Map<UUID, CompletedPlanSaveCommand.Place> attractions = new HashMap<>();
            request.places().forEach(place -> attractions.put(place.clientPlaceId(),
                    stored(selections.verifyAttraction(place.selectionToken(), userId, request.regionId()),
                            place.displayName(), null, place.stayMinutes())));
            CompletedPlanSaveCommand.Place hotel = request.hotelSelectionToken() == null ? null
                    : stored(selections.verifyHotel(request.hotelSelectionToken(), userId, request.regionId()),
                            request.hotelDisplayName(), null, null);

            Map<LocalDate, Map<MealType, Coordinate>> mealCoordinates = new HashMap<>();
            Map<CompletedPlanSaveCommand.MealKey, CompletedPlanSaveCommand.Place> restaurants = new HashMap<>();
            Set<CompletedPlanSaveCommand.MealKey> expected = new HashSet<>();
            command.conditions().days().forEach(day -> {
                for (MealType type : MealType.values()) {
                    if (MealSlotPolicy.createSlot(day, type).isPresent())
                        expected.add(new CompletedPlanSaveCommand.MealKey(day.date(), type));
                }
            });
            Set<CompletedPlanSaveCommand.MealKey> supplied = new HashSet<>();
            for (TravelPlanCreateApiRequest.Meal meal : request.meals()) {
                if (meal == null || meal.date() == null || meal.mealType() == null) fail();
                var key = new CompletedPlanSaveCommand.MealKey(meal.date(), meal.mealType());
                if (!supplied.add(key)) fail();
                if (meal.restaurantSelectionToken() == null) {
                    if (!defaultMealName(meal.mealType()).equals(meal.displayName()) || meal.memo() != null) fail();
                } else {
                    if (!text(meal.displayName(), 50) || meal.memo() != null && meal.memo().length() > 1000) fail();
                    PlanningPlaceSelection selected = selections.verifyRestaurant(
                            meal.restaurantSelectionToken(), userId, request.regionId());
                    restaurants.put(key, stored(selected, meal.displayName(), meal.memo(), null));
                    mealCoordinates.computeIfAbsent(meal.date(), ignored -> new EnumMap<>(MealType.class))
                            .put(meal.mealType(), new Coordinate(selected.latitude(), selected.longitude()));
                }
            }
            if (!supplied.equals(expected)) fail();

            var calculated = calculations.calculate(command, mealCoordinates);
            var saveCommand = new CompletedPlanSaveCommand(userId, request.title(), request.regionId(),
                    region.name(), command.conditions(), request.foods(), attractions, hotel,
                    restaurants, calculated.days());
            return transactions.execute(status -> {
                long id = saves.save(saveCommand);
                TravelPlanCreateApiResponse response = response(id, calculated.warnings().stream()
                        .map(Enum::name).toList());
                if (!executions.markSucceeded(lease)) throw new IllegalStateException("Request completion failed");
                return response;
            });
        } catch (RuntimeException exception) {
            executions.releaseAfterFailure(lease);
            throw exception;
        }
    }

    private TravelPlanCreateApiResponse response(long id, List<String> warnings) {
        TravelPlan plan = plans.findById(id).orElseThrow();
        Map<Long, PlanPlace> savedPlaces = places.findByTravelPlanId(id).stream()
                .collect(Collectors.toMap(PlanPlace::id, place -> place));
        List<TravelPlanCreateApiResponse.Day> savedDays = new ArrayList<>();
        for (TravelPlanDay day : days.findByTravelPlanIdOrderByDayNumber(id)) {
            List<TravelPlanCreateApiResponse.Item> savedItems = items.findByTravelPlanDayIdOrderByItemOrder(day.id())
                    .stream().map(item -> itemResponse(item, savedPlaces)).toList();
            savedDays.add(new TravelPlanCreateApiResponse.Day(day.dayNumber(), day.travelDate(),
                    day.activityStartTime().format(TIME), day.activityEndTime().format(TIME), savedItems));
        }
        return new TravelPlanCreateApiResponse(id, plan.title(),
                new TravelPlanCreateApiResponse.Region(plan.regionId(), plan.regionDisplayName()),
                plan.travelMode(), plan.startDate(), plan.endDate(), warnings,
                savedPlaces.values().stream().filter(place -> place.role() == PlanPlace.Role.HOTEL)
                        .findFirst().map(place -> new TravelPlanCreateApiResponse.Hotel(place.id(),
                                place.displayName(), place.memo(), place.placeUrl())).orElse(null), savedDays);
    }

    private static TravelPlanCreateApiResponse.Item itemResponse(TravelPlanItem item,
            Map<Long, PlanPlace> places) {
        PlanPlace place = places.get(item.planPlaceId());
        String name = place == null && item.itemType() == TravelPlanItem.Type.MEAL
                ? defaultMealName(item.startTime().isBefore(LocalTime.of(14, 0))
                        ? MealType.LUNCH : MealType.DINNER)
                : place == null ? null : place.displayName();
        return new TravelPlanCreateApiResponse.Item(item.id(), item.itemOrder(), item.itemType().name(),
                item.planPlaceId(), name, place == null ? null : place.placeUrl(),
                item.startTime().format(TIME), item.endTime().format(TIME),
                place == null ? null : place.stayMinutes(), item.estimatedMinutes(),
                place == null ? null : place.memo());
    }

    private static CompletedPlanSaveCommand.Place stored(PlanningPlaceSelection selected,
            String name, String memo, Integer stayMinutes) {
        return new CompletedPlanSaveCommand.Place(selected.kakaoPlaceId(),
                selected.placeUrl().toString(), name, memo, stayMinutes);
    }

    private static String defaultMealName(MealType type) {
        return type == MealType.LUNCH ? "점심 식사" : "저녁 식사";
    }

    private static boolean text(String value, int max) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= max;
    }

    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED); }
    private static void fail() { throw invalid(); }
}
