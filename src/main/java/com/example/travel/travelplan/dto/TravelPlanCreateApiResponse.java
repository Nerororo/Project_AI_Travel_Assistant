package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;

import java.time.LocalDate;
import java.util.List;

public record TravelPlanCreateApiResponse(long travelPlanId, String title, Region region,
        TravelMode travelMode, LocalDate startDate, LocalDate endDate,
        List<String> warnings, List<Day> days) {
    public record Region(String regionId, String displayName) { }
    public record Day(long day, LocalDate date, String activityStartTime,
            String activityEndTime, List<Item> items) { }
    public record Item(long itemId, int order, String type, Long planPlaceId,
            String displayName, String placeUrl, String startTime, String endTime,
            Integer stayMinutes, Integer estimatedMinutes, String memo) { }
}
