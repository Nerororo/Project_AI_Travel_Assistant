package com.example.travel.travelplan.dto;

import com.example.travel.travelplan.domain.MealType;

import java.time.LocalDateTime;
import java.util.UUID;

/** Time-only estimate. It never exposes provider coordinates or boundary IDs. */
public record EstimatedItem(
		int order,
		Type type,
		UUID clientPlaceId,
		String displayName,
		MealType mealType,
		LocalDateTime startTime,
		LocalDateTime endTime,
		Integer estimatedMinutes
) {
	public enum Type { VISIT, MOVE, MEAL }
}
