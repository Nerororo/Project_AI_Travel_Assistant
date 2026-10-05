package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;
import java.time.Instant;
import java.time.LocalDate;

public record TravelPlanListItemResponse(long travelPlanId, String title,
        TravelPlanCreateApiResponse.Region region, TravelMode travelMode,
        LocalDate startDate, LocalDate endDate, Instant createdAt) { }
