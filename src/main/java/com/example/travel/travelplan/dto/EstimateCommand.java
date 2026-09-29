package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.Coordinate;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Boundary tokens and verified in-memory places for one estimate invocation. */
public record EstimateCommand(
		long userId,
		String regionId,
		TravelConditions conditions,
		String startBoundarySelectionToken,
		String endBoundarySelectionToken,
		List<EstimateVisit> visits,
		Coordinate hotelCoordinate
) {
	public EstimateCommand {
		if (userId <= 0 || regionId == null || regionId.isBlank()
				|| startBoundarySelectionToken == null || startBoundarySelectionToken.isBlank()
				|| endBoundarySelectionToken == null || endBoundarySelectionToken.isBlank()) {
			throw new IllegalArgumentException("user, region and boundary tokens are required");
		}
		Objects.requireNonNull(conditions, "conditions must not be null");
		visits = List.copyOf(Objects.requireNonNull(visits, "visits must not be null"));
		if ((conditions.period().dayCount() == 1) != (hotelCoordinate == null)) {
			throw new IllegalArgumentException("hotel is required only for multi-day travel");
		}
		if (visits.size() > conditions.period().dayCount() * 5) {
			throw new IllegalArgumentException("too many visits for the travel period");
		}
		Set<UUID> ids = new HashSet<>();
		for (EstimateVisit visit : visits) {
			if (!ids.add(visit.clientPlaceId())) {
				throw new IllegalArgumentException("clientPlaceId must be unique");
			}
		}
	}
}
