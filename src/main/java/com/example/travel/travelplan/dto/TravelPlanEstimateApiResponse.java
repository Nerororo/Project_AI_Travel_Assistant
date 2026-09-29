package com.example.travel.travelplan.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HTTP output intentionally excludes tokens, provider fields and coordinates. */
public record TravelPlanEstimateApiResponse(boolean routeVerified, List<Day> days) {
	public TravelPlanEstimateApiResponse {
		days = List.copyOf(days);
	}

	public record Day(LocalDate date, List<Item> items) {
		public Day {
			items = List.copyOf(items);
		}
	}

	public record Item(
			UUID clientPlaceId,
			int order,
			EstimatedItem.Type type,
			String displayName,
			String startTime,
			String endTime,
			Integer estimatedMinutes
	) {
	}
}
