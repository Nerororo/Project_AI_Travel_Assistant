package com.example.travel.route.service;

import com.example.travel.user.dto.UsageDenialScope;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Request-scoped route verification result. The fallback reason is for warning and observation
 * mapping and must not be persisted with an itinerary.
 */
public record RouteTravelTimeResult(
		List<RouteSegmentTravelTime> segmentTravelTimes,
		RouteFallbackReason fallbackReason,
		Set<UsageDenialScope> quotaDeniedScopes
) {

	public RouteTravelTimeResult {
		Objects.requireNonNull(segmentTravelTimes, "segmentTravelTimes must not be null");
		segmentTravelTimes = List.copyOf(segmentTravelTimes);
		Objects.requireNonNull(fallbackReason, "fallbackReason must not be null");
		Objects.requireNonNull(quotaDeniedScopes, "quotaDeniedScopes must not be null");
		quotaDeniedScopes = Set.copyOf(quotaDeniedScopes);
		if (fallbackReason == RouteFallbackReason.QUOTA_UNAVAILABLE && quotaDeniedScopes.isEmpty()) {
			throw new IllegalArgumentException("Quota fallback requires denied scopes");
		}
		if (fallbackReason != RouteFallbackReason.QUOTA_UNAVAILABLE && !quotaDeniedScopes.isEmpty()) {
			throw new IllegalArgumentException("Only quota fallback can have denied scopes");
		}
	}

	public static RouteTravelTimeResult verified(List<RouteSegmentTravelTime> segmentTravelTimes) {
		return new RouteTravelTimeResult(segmentTravelTimes, RouteFallbackReason.NONE, Set.of());
	}

	public static RouteTravelTimeResult fallback(
			List<RouteSegmentTravelTime> segmentTravelTimes,
			RouteFallbackReason fallbackReason
	) {
		return fallback(segmentTravelTimes, fallbackReason, Set.of());
	}

	public static RouteTravelTimeResult fallback(
			List<RouteSegmentTravelTime> segmentTravelTimes,
			RouteFallbackReason fallbackReason,
			Set<UsageDenialScope> quotaDeniedScopes
	) {
		if (fallbackReason == RouteFallbackReason.NONE) {
			throw new IllegalArgumentException("fallbackReason must identify a fallback");
		}
		return new RouteTravelTimeResult(segmentTravelTimes, fallbackReason, quotaDeniedScopes);
	}

	public boolean fallbackApplied() {
		return fallbackReason != RouteFallbackReason.NONE;
	}
}
