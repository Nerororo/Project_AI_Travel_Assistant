package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.user.dto.UsageDenialScope;

import java.util.Objects;
import java.util.Set;

/**
 * Aggregate-only observation contract. It intentionally contains no user, place, or coordinate data.
 */
public record RouteObservationEvent(
		TravelMode travelMode,
		RouteObservationOutcome outcome,
		Set<UsageDenialScope> quotaDeniedScopes
) {

	public RouteObservationEvent {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(outcome, "outcome must not be null");
		Objects.requireNonNull(quotaDeniedScopes, "quotaDeniedScopes must not be null");
		quotaDeniedScopes = Set.copyOf(quotaDeniedScopes);
		if (outcome == RouteObservationOutcome.QUOTA_FALLBACK && quotaDeniedScopes.isEmpty()) {
			throw new IllegalArgumentException("Quota fallback requires denied scopes");
		}
		if (outcome != RouteObservationOutcome.QUOTA_FALLBACK && !quotaDeniedScopes.isEmpty()) {
			throw new IllegalArgumentException("Only quota fallback can have denied scopes");
		}
	}
}
