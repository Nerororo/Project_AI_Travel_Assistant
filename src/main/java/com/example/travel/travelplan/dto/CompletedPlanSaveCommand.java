package com.example.travel.travelplan.dto;

import com.example.travel.travelplan.domain.MealType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Only verified, storage-eligible place fields and the completed calculation. */
public record CompletedPlanSaveCommand(long userId, String title, String regionId,
        String regionDisplayName, TravelConditions conditions, List<String> foods,
        Map<UUID, Place> attractions, Place hotel, Map<MealKey, Place> restaurants,
        List<EstimatedDay> days) {

    public CompletedPlanSaveCommand {
        foods = List.copyOf(foods);
        attractions = Map.copyOf(attractions);
        restaurants = Map.copyOf(restaurants);
        days = List.copyOf(days);
    }

    public record Place(String kakaoPlaceId, String placeUrl, String displayName,
            String memo, Integer stayMinutes) { }

    public record MealKey(LocalDate date, MealType type) { }
}
