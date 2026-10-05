package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.travelplan.domain.PlanPlace;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.dto.TravelPlanPatchRequest;
import com.example.travel.travelplan.repository.FoodPreferenceRepository;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.travelplan.repository.TravelPlanShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TravelPlanMutationService {
    private final TravelPlanRepository plans;
    private final TravelPlanShareRepository shares;
    private final TravelPlanItemRepository items;
    private final TravelPlanDayRepository days;
    private final PlanPlaceRepository places;
    private final FoodPreferenceRepository foods;

    public TravelPlanMutationService(TravelPlanRepository plans, TravelPlanShareRepository shares,
            TravelPlanItemRepository items, TravelPlanDayRepository days,
            PlanPlaceRepository places, FoodPreferenceRepository foods) {
        this.plans = plans;
        this.shares = shares;
        this.items = items;
        this.days = days;
        this.places = places;
        this.foods = foods;
    }

    @Transactional
    public void patch(long userId, long travelPlanId, TravelPlanPatchRequest request) {
        TravelPlan plan = owned(userId, travelPlanId);
        if (request == null) throw invalid();
        Map<Long, PlanPlace> savedPlaces = places.findByTravelPlanId(travelPlanId).stream()
                .collect(Collectors.toMap(PlanPlace::id, place -> place));
        Set<Long> editedIds = new HashSet<>();
        for (TravelPlanPatchRequest.PlaceEdit edit : request.placeEdits()) {
            if (!editedIds.add(edit.planPlaceId()) || !savedPlaces.containsKey(edit.planPlaceId())) throw invalid();
        }
        if (request.title() != null) plan.rename(request.title());
        for (TravelPlanPatchRequest.PlaceEdit edit : request.placeEdits()) {
            PlanPlace place = savedPlaces.get(edit.planPlaceId());
            place.edit(edit.changeName() ? edit.displayName() : place.displayName(),
                    edit.changeMemo() ? edit.memo() : place.memo());
        }
    }

    @Transactional
    public void delete(long userId, long travelPlanId) {
        TravelPlan plan = owned(userId, travelPlanId);
        shares.findById(travelPlanId).ifPresent(shares::delete);
        shares.flush();
        items.deleteByTravelPlanId(travelPlanId);
        items.flush();
        days.deleteByTravelPlanId(travelPlanId);
        days.flush();
        places.deleteByTravelPlanId(travelPlanId);
        places.flush();
        foods.deleteByTravelPlanId(travelPlanId);
        foods.flush();
        plans.delete(plan);
        plans.flush();
    }

    private TravelPlan owned(long userId, long travelPlanId) {
        return plans.findByIdAndUserId(travelPlanId, userId).orElseGet(() -> {
            throw new ApiException(plans.existsById(travelPlanId)
                    ? ErrorCode.ACCESS_DENIED : ErrorCode.TRAVEL_PLAN_NOT_FOUND);
        });
    }

    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED); }
}
