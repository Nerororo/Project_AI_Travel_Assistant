package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.travelplan.domain.TravelPlanInputPolicy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** A request-scoped, already verified attraction used only while estimating. */
public record EstimateVisit(
		UUID clientPlaceId,
		Coordinate coordinate,
		String displayName,
		int stayMinutes,
		LocalDate day,
		Integer order
) {
	public EstimateVisit {
		Objects.requireNonNull(clientPlaceId, "clientPlaceId must not be null");
		Objects.requireNonNull(coordinate, "coordinate must not be null");
		if (displayName == null || displayName.trim().isEmpty()
				|| displayName.trim().length() > 50) {
			throw new IllegalArgumentException("displayName must be between 1 and 50 characters");
		}
		displayName = displayName.trim();
		TravelPlanInputPolicy.validateStayMinutes(stayMinutes);
		if ((day == null) != (order == null) || (order != null && order < 1)) {
			throw new IllegalArgumentException("day and positive order must be supplied together");
		}
	}
}
