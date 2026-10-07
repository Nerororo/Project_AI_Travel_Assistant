package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.MealType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record TravelPlanCreateApiRequest(
        @NotBlank String title,
        @NotBlank String regionId,
        @NotNull TravelMode travelMode,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        @NotBlank String startBoundarySelectionToken,
        @NotBlank String endBoundarySelectionToken,
        @NotNull @Size(min = 1, max = 7) @Valid List<TravelPlanEstimateApiRequest.Day> days,
        @NotNull @Valid List<TravelPlanEstimateApiRequest.Place> places,
        String hotelSelectionToken,
        String hotelDisplayName,
        Integer mealTravelBufferMinutes,
        @NotNull @Size(min = 1, max = 5) List<String> foods,
        @NotNull List<@Valid Meal> meals
) {
    public TravelPlanEstimateApiRequest estimate() {
        return new TravelPlanEstimateApiRequest(regionId, travelMode, startDate, endDate,
                startBoundarySelectionToken, endBoundarySelectionToken, days, places,
                hotelSelectionToken, mealTravelBufferMinutes, foods);
    }

    public record Meal(
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @NotNull MealType mealType,
            String restaurantSelectionToken,
            String displayName,
            String memo
    ) { }
}
