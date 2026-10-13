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
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 100) String regionId,
        @NotNull TravelMode travelMode,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        @NotBlank @Size(max = 2048) String startBoundarySelectionToken,
        @NotBlank @Size(max = 2048) String endBoundarySelectionToken,
        @NotNull @Size(min = 1, max = 7) @Valid List<TravelPlanEstimateApiRequest.Day> days,
        @NotNull @Size(max = 35) @Valid List<TravelPlanEstimateApiRequest.Place> places,
        @Size(max = 2048) String hotelSelectionToken,
        @Size(max = 50) String hotelDisplayName,
        Integer mealTravelBufferMinutes,
        @NotNull @Size(min = 1, max = 5) List<@Size(max = 50) String> foods,
        @NotNull @Size(max = 14) List<@Valid Meal> meals
) {
    public TravelPlanEstimateApiRequest estimate() {
        return new TravelPlanEstimateApiRequest(regionId, travelMode, startDate, endDate,
                startBoundarySelectionToken, endBoundarySelectionToken, days, places,
                hotelSelectionToken, mealTravelBufferMinutes, foods);
    }

    public record Meal(
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @NotNull MealType mealType,
            @Size(max = 2048) String restaurantSelectionToken,
            @Size(max = 50) String displayName,
            @Size(max = 1000) String memo
    ) { }
}
