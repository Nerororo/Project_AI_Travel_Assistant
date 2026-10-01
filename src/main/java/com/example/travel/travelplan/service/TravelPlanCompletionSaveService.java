package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.FoodPreference;
import com.example.travel.travelplan.domain.PlanPlace;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.domain.TravelPlanDay;
import com.example.travel.travelplan.domain.TravelPlanInputPolicy;
import com.example.travel.travelplan.domain.TravelPlanItem;
import com.example.travel.travelplan.dto.CompletedPlanSaveCommand;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.repository.FoodPreferenceRepository;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persists a fully calculated plan; no token, coordinate, warning or provider response enters this service. */
@Service
public class TravelPlanCompletionSaveService {
    private static final String DEFAULT_HOTEL_DISPLAY_NAME = "숙소";

    private final TransactionTemplate transactions;
    private final TravelPlanRepository plans;
    private final TravelPlanDayRepository days;
    private final TravelPlanItemRepository items;
    private final PlanPlaceRepository places;
    private final FoodPreferenceRepository foods;

    public TravelPlanCompletionSaveService(TransactionTemplate transactions,
            TravelPlanRepository plans, TravelPlanDayRepository days,
            TravelPlanItemRepository items, PlanPlaceRepository places,
            FoodPreferenceRepository foods) {
        this.transactions = transactions;
        this.plans = plans;
        this.days = days;
        this.items = items;
        this.places = places;
        this.foods = foods;
    }

    public long save(CompletedPlanSaveCommand command) {
        validate(command);
        return transactions.execute(status -> persist(command));
    }

    private long persist(CompletedPlanSaveCommand command) {
        var conditions = command.conditions();
        long planId = plans.save(new TravelPlan(command.userId(), command.title().trim(),
                command.regionId(), command.regionDisplayName(), conditions.period().startDate(),
                conditions.period().endDate(), conditions.travelMode(),
                conditions.mealTravelBufferMinutes())).id();
        command.foods().forEach(food -> foods.save(new FoodPreference(planId, food.trim())));
        Map<UUID, Long> attractionIds = new HashMap<>();
        command.attractions().forEach((key, place) -> attractionIds.put(key,
                savePlace(planId, place, PlanPlace.Role.ATTRACTION)));
        if (command.hotel() != null) savePlace(planId, command.hotel(), PlanPlace.Role.HOTEL);
        Map<CompletedPlanSaveCommand.MealKey, Long> restaurantIds = new HashMap<>();
        command.restaurants().forEach((key, place) -> restaurantIds.put(key,
                savePlace(planId, place, PlanPlace.Role.RESTAURANT)));

        for (int dayNumber = 0; dayNumber < command.days().size(); dayNumber++) {
            EstimatedDay calculated = command.days().get(dayNumber);
            DailyActivityWindow window = conditions.days().stream()
                    .filter(day -> day.date().equals(calculated.date())).findFirst().orElseThrow();
            long dayId = days.save(new TravelPlanDay(planId, dayNumber + 1, calculated.date(),
                    window.startTime(), window.endTime())).id();
            int order = 0;
            for (EstimatedItem item : calculated.items()) {
                // A provider may report a valid zero-minute leg; it needs no MOVE row.
                if (item.type() == EstimatedItem.Type.MOVE && item.estimatedMinutes() == 0) continue;
                Long placeId = switch (item.type()) {
                    case VISIT -> attractionIds.get(item.clientPlaceId());
                    case MEAL -> restaurantIds.get(new CompletedPlanSaveCommand.MealKey(
                            calculated.date(), item.mealType()));
                    case MOVE -> null;
                };
                items.save(new TravelPlanItem(dayId, planId, ++order,
                        TravelPlanItem.Type.valueOf(item.type().name()), placeId,
                        item.startTime().toLocalTime(), item.endTime().toLocalTime(),
                        item.type() == EstimatedItem.Type.MOVE ? item.estimatedMinutes() : null));
            }
        }
        return planId;
    }

    private long savePlace(long planId, CompletedPlanSaveCommand.Place place, PlanPlace.Role role) {
        String displayName = role == PlanPlace.Role.HOTEL && place.displayName() == null
                ? DEFAULT_HOTEL_DISPLAY_NAME : place.displayName().trim();
        return places.save(new PlanPlace(planId, place.kakaoPlaceId(), place.placeUrl(), role,
                displayName, place.memo(), place.stayMinutes())).id();
    }

    private static void validate(CompletedPlanSaveCommand command) {
        if (command == null || command.userId() <= 0 || !text(command.title(), 100)
                || !text(command.regionId(), 100) || !text(command.regionDisplayName(), 100)
                || command.conditions() == null || command.days().size() != command.conditions().period().dayCount()
                || command.foods().isEmpty() || command.foods().size() > 5
                || (command.hotel() == null) != (command.conditions().period().dayCount() == 1)) fail();
        Set<String> uniqueFoods = new HashSet<>();
        for (String food : command.foods()) {
            if (!text(food, 50) || !uniqueFoods.add(food.trim())) fail();
        }
        command.attractions().values().forEach(place -> validatePlace(place, PlanPlace.Role.ATTRACTION));
        if (command.hotel() != null) validatePlace(command.hotel(), PlanPlace.Role.HOTEL);
        command.restaurants().values().forEach(place -> validatePlace(place, PlanPlace.Role.RESTAURANT));
        Set<UUID> seenVisits = new HashSet<>();
        Set<CompletedPlanSaveCommand.MealKey> seenMeals = new HashSet<>();
        List<LocalDate> dates = command.conditions().period().dates();
        for (int index = 0; index < dates.size(); index++) {
            EstimatedDay day = command.days().get(index);
            if (day == null || !dates.get(index).equals(day.date())) fail();
            DailyActivityWindow window = command.conditions().days().stream()
                    .filter(value -> value.date().equals(day.date())).findFirst().orElseThrow();
            int visits = 0;
            int expectedOrder = 1;
            for (EstimatedItem item : day.items()) {
                if (item == null || item.order() != expectedOrder++ || item.type() == null
                        || item.startTime() == null || item.endTime() == null
                        || !day.date().equals(item.startTime().toLocalDate())
                        || !day.date().equals(item.endTime().toLocalDate())
                        || item.startTime().toLocalTime().isBefore(window.startTime())
                        || item.endTime().toLocalTime().isAfter(window.endTime())) fail();
                switch (item.type()) {
                    case VISIT -> {
                        if (item.clientPlaceId() == null || !command.attractions().containsKey(item.clientPlaceId())
                                || !seenVisits.add(item.clientPlaceId()) || item.estimatedMinutes() != null) fail();
                        visits++;
                    }
                    case MEAL -> {
                        if (item.mealType() == null || item.estimatedMinutes() != null
                                || item.startTime().toLocalTime().isBefore(item.mealType().allowedStart())
                                || item.endTime().toLocalTime().isAfter(item.mealType().allowedEnd())
                                || Duration.between(item.startTime(), item.endTime()).toMinutes() != 60
                                || !seenMeals.add(new CompletedPlanSaveCommand.MealKey(day.date(), item.mealType()))) fail();
                    }
                    case MOVE -> {
                        Integer minutes = item.estimatedMinutes();
                        if (minutes == null || minutes < 0 || minutes % 10 != 0
                                || Duration.between(item.startTime(), item.endTime()).toMinutes() != minutes) fail();
                    }
                }
            }
            if (visits > 5 || day.plannedEndTime() == null
                    || day.plannedEndTime().toLocalTime().isAfter(window.endTime())) fail();
        }
        if (!seenVisits.equals(command.attractions().keySet())
                || !seenMeals.containsAll(command.restaurants().keySet())) fail();
    }

    private static void validatePlace(CompletedPlanSaveCommand.Place place, PlanPlace.Role role) {
        if (place == null || place.kakaoPlaceId() == null || !place.kakaoPlaceId().matches("[0-9]{1,30}")
                || !("https://place.map.kakao.com/" + place.kakaoPlaceId()).equals(place.placeUrl())
                || !(role == PlanPlace.Role.HOTEL && place.displayName() == null)
                        && !text(place.displayName(), 50)
                || (place.memo() != null && place.memo().length() > 1000)
                || (role == PlanPlace.Role.ATTRACTION) != (place.stayMinutes() != null)) fail();
        if (role == PlanPlace.Role.ATTRACTION) {
            try { TravelPlanInputPolicy.validateStayMinutes(place.stayMinutes()); }
            catch (IllegalArgumentException exception) { fail(); }
        }
    }

    private static boolean text(String value, int max) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= max;
    }

    private static void fail() { throw new ApiException(ErrorCode.VALIDATION_FAILED); }
}
