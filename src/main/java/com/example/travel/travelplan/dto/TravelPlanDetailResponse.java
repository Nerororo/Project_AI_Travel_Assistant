package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;
import java.time.LocalDate;
import java.util.List;

public record TravelPlanDetailResponse(long travelPlanId, String title,
        TravelPlanCreateApiResponse.Region region, TravelMode travelMode,
        LocalDate startDate, LocalDate endDate, List<TravelPlanCreateApiResponse.Day> days) { }
