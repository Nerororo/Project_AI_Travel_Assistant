package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.travelplan.domain.PlanPlace;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.domain.TravelPlanDay;
import com.example.travel.travelplan.domain.TravelPlanItem;
import com.example.travel.travelplan.dto.TravelPlanCreateApiResponse;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanListItemResponse;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TravelPlanReadService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final TravelPlanRepository plans;
    private final TravelPlanDayRepository days;
    private final TravelPlanItemRepository items;
    private final PlanPlaceRepository places;

    public TravelPlanReadService(TravelPlanRepository plans, TravelPlanDayRepository days,
            TravelPlanItemRepository items, PlanPlaceRepository places) {
        this.plans = plans;
        this.days = days;
        this.items = items;
        this.places = places;
    }

    @Transactional(readOnly = true)
    public List<TravelPlanListItemResponse> list(long userId) {
        return plans.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(plan -> new TravelPlanListItemResponse(plan.id(), plan.title(), region(plan),
                        plan.travelMode(), plan.startDate(), plan.endDate(), plan.createdAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TravelPlanDetailResponse detail(long userId, long travelPlanId) {
        TravelPlan plan = plans.findByIdAndUserId(travelPlanId, userId).orElseGet(() -> {
            throw new ApiException(plans.existsById(travelPlanId)
                    ? ErrorCode.ACCESS_DENIED : ErrorCode.TRAVEL_PLAN_NOT_FOUND);
        });
        return savedDetail(plan);
    }

    @Transactional(readOnly = true)
    public TravelPlanDetailResponse sharedDetail(long travelPlanId) {
        TravelPlan plan = plans.findById(travelPlanId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRAVEL_PLAN_NOT_FOUND));
        return savedDetail(plan);
    }

    private TravelPlanDetailResponse savedDetail(TravelPlan plan) {
        long travelPlanId = plan.id();
        Map<Long, PlanPlace> savedPlaces = places.findByTravelPlanId(travelPlanId).stream()
                .collect(Collectors.toMap(PlanPlace::id, place -> place));
        List<TravelPlanCreateApiResponse.Day> savedDays = days.findByTravelPlanIdOrderByDayNumber(travelPlanId)
                .stream().map(day -> day(day, savedPlaces)).toList();
        return new TravelPlanDetailResponse(plan.id(), plan.title(), region(plan), plan.travelMode(),
                plan.startDate(), plan.endDate(),
                savedPlaces.values().stream().filter(place -> place.role() == PlanPlace.Role.HOTEL)
                        .findFirst().map(place -> new TravelPlanCreateApiResponse.Hotel(place.id(),
                                place.displayName(), place.memo(), place.placeUrl())).orElse(null), savedDays);
    }

    private TravelPlanCreateApiResponse.Day day(TravelPlanDay day, Map<Long, PlanPlace> savedPlaces) {
        List<TravelPlanCreateApiResponse.Item> savedItems = items.findByTravelPlanDayIdOrderByItemOrder(day.id())
                .stream().map(item -> item(item, savedPlaces)).toList();
        return new TravelPlanCreateApiResponse.Day(day.dayNumber(), day.travelDate(),
                day.activityStartTime().format(TIME), day.activityEndTime().format(TIME), savedItems);
    }

    private static TravelPlanCreateApiResponse.Item item(TravelPlanItem item, Map<Long, PlanPlace> places) {
        PlanPlace place = places.get(item.planPlaceId());
        String name = place == null && item.itemType() == TravelPlanItem.Type.MEAL
                ? item.startTime().isBefore(LocalTime.of(14, 0)) ? "점심 식사" : "저녁 식사"
                : place == null ? null : place.displayName();
        return new TravelPlanCreateApiResponse.Item(item.id(), item.itemOrder(), item.itemType().name(),
                item.planPlaceId(), name, place == null ? null : place.placeUrl(),
                item.startTime().format(TIME), item.endTime().format(TIME),
                place == null ? null : place.stayMinutes(), item.estimatedMinutes(),
                place == null ? null : place.memo());
    }

    private static TravelPlanCreateApiResponse.Region region(TravelPlan plan) {
        return new TravelPlanCreateApiResponse.Region(plan.regionId(), plan.regionDisplayName());
    }
}
