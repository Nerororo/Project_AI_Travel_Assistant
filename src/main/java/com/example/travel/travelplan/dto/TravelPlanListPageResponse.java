package com.example.travel.travelplan.dto;

import java.util.List;

public record TravelPlanListPageResponse(List<TravelPlanListItemResponse> items,
        int page, int size, boolean hasNext) {
}
