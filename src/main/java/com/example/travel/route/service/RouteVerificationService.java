package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.user.dto.UsageReservationResult;
import com.example.travel.user.dto.UsageReservationLease;

import java.util.List;
import java.util.Objects;

/**
 * Coordinates quota reservation and route verification without opening a database transaction.
 */
public class RouteVerificationService {

	private final RouteQuotaService routeQuotaService;
	private final RouteService routeService;

	public RouteVerificationService(RouteQuotaService routeQuotaService, RouteService routeService) {
		this.routeQuotaService = Objects.requireNonNull(
				routeQuotaService,
				"routeQuotaService must not be null"
		);
		this.routeService = Objects.requireNonNull(routeService, "routeService must not be null");
	}

	public RouteVerificationResult verify(
			long userId,
			TravelMode travelMode,
			List<RouteSegment> orderedSegments
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(orderedSegments, "orderedSegments must not be null");
		List<RouteSegment> segments = List.copyOf(orderedSegments);

		UsageReservationLease reservation = routeQuotaService.reserve(userId, travelMode, segments);
		if (!reservation.result().acquired()) {
			return fallbackResult(
					travelMode,
					routeService.fallbackWithHaversine(
							travelMode,
							segments,
							RouteFallbackReason.QUOTA_UNAVAILABLE,
							reservation.result().deniedScopes()
					),
					RouteObservationOutcome.QUOTA_FALLBACK
			);
		}

		RouteTravelTimeResult travelTimes = routeService.findEstimatedTravelTimes(
				travelMode,
				segments,
				() -> routeQuotaService.reserve(userId, travelMode, 1).result(),
				unusedRequestCount -> routeQuotaService.release(
						userId,
						travelMode,
						reservation,
						unusedRequestCount
				)
		);
		if (travelTimes.fallbackApplied()) {
			return fallbackResult(
					travelMode,
					travelTimes,
					observationOutcome(travelTimes.fallbackReason())
			);
		}

		return new RouteVerificationResult(
				travelTimes,
				List.of(),
				new RouteObservationEvent(
						travelMode,
						RouteObservationOutcome.PROVIDER_VERIFIED,
						java.util.Set.of()
				)
		);
	}

	private RouteObservationOutcome observationOutcome(RouteFallbackReason fallbackReason) {
		return switch (fallbackReason) {
			case QUOTA_UNAVAILABLE -> RouteObservationOutcome.QUOTA_FALLBACK;
			case TECHNICAL_FAILURE -> RouteObservationOutcome.TECHNICAL_FAILURE_FALLBACK;
			case NONE -> throw new IllegalArgumentException("A fallback result requires a fallback reason");
		};
	}

	private RouteVerificationResult fallbackResult(
			TravelMode travelMode,
			RouteTravelTimeResult travelTimes,
			RouteObservationOutcome outcome
	) {
		return new RouteVerificationResult(
				travelTimes,
				List.of(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED),
				new RouteObservationEvent(travelMode, outcome, travelTimes.quotaDeniedScopes())
		);
	}
}
