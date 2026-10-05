package com.example.travel.route.service;

import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.HaversineTravelTimeEstimator;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.algorithm.TravelTimePolicy;
import com.example.travel.route.client.CarRouteClient;
import com.example.travel.route.client.PublicTransitRouteClient;
import com.example.travel.route.client.RouteClientException;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.user.dto.UsageDenialScope;
import com.example.travel.user.dto.UsageReservationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Verifies ordered adjacent segments with the client for one travel mode.
 */
public class RouteService {

	private static final long ROUNDING_UNIT_SECONDS = 600L;
	private static final int ROUNDING_UNIT_MINUTES = 10;

	private final CarRouteClient carRouteClient;
	private final PublicTransitRouteClient publicTransitRouteClient;

	public RouteService(CarRouteClient carRouteClient, PublicTransitRouteClient publicTransitRouteClient) {
		this.carRouteClient = Objects.requireNonNull(carRouteClient, "carRouteClient must not be null");
		this.publicTransitRouteClient = Objects.requireNonNull(
				publicTransitRouteClient,
				"publicTransitRouteClient must not be null"
		);
	}

	RouteResult findRoute(TravelMode travelMode, RouteSegment segment) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(segment, "segment must not be null");

		return switch (travelMode) {
			case CAR -> carRouteClient.findRoute(segment);
			case PUBLIC_TRANSIT -> publicTransitRouteClient.findRoute(segment);
		};
	}

	/**
	 * Keeps segment order, stops at the first normal route absence, retries transient technical
	 * failures once, and replaces the whole request with Haversine estimates if that retry also
	 * fails transiently.
	 */
	RouteTravelTimeResult findEstimatedTravelTimes(
			TravelMode travelMode,
			List<RouteSegment> orderedSegments
	) {
		return findEstimatedTravelTimes(
				travelMode,
				orderedSegments,
				UsageReservationResult::success,
				ignored -> { }
		);
	}

	RouteTravelTimeResult findEstimatedTravelTimes(
			TravelMode travelMode,
			List<RouteSegment> orderedSegments,
			Supplier<UsageReservationResult> retryQuotaReservation
	) {
		return findEstimatedTravelTimes(
				travelMode,
				orderedSegments,
				retryQuotaReservation,
				ignored -> { }
		);
	}

	RouteTravelTimeResult findEstimatedTravelTimes(
			TravelMode travelMode,
			List<RouteSegment> orderedSegments,
			Supplier<UsageReservationResult> retryQuotaReservation,
			LongConsumer unusedInitialQuotaRelease
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(orderedSegments, "orderedSegments must not be null");
		Objects.requireNonNull(retryQuotaReservation, "retryQuotaReservation must not be null");
		Objects.requireNonNull(unusedInitialQuotaRelease, "unusedInitialQuotaRelease must not be null");
		List<RouteSegment> segments = List.copyOf(orderedSegments);

		List<RouteSegmentTravelTime> providerTravelTimes = new ArrayList<>(segments.size());
		for (int index = 0; index < segments.size(); index++) {
			RouteSegment segment = segments.get(index);
			RouteAttempt attempt;
			try {
				attempt = findRouteWithRetry(
						travelMode,
						segment,
						retryQuotaReservation
				);
			}
			catch (RouteClientException exception) {
				releaseUnusedInitialQuota(segments.size(), index, unusedInitialQuotaRelease);
				if (!exception.failure().isTransientTechnicalFailure()) {
					throw exception;
				}
				return fallbackWithHaversine(
						travelMode,
						segments,
						RouteFallbackReason.TECHNICAL_FAILURE
				);
			}
			catch (RuntimeException exception) {
				releaseUnusedInitialQuota(segments.size(), index, unusedInitialQuotaRelease);
				throw exception;
			}
			if (attempt.retryQuotaUnavailable()) {
				releaseUnusedInitialQuota(segments.size(), index, unusedInitialQuotaRelease);
				return fallbackWithHaversine(
						travelMode,
						segments,
						RouteFallbackReason.QUOTA_UNAVAILABLE,
						attempt.quotaDeniedScopes()
				);
			}
			providerTravelTimes.add(toTravelTime(attempt.result()));
			if (attempt.result() instanceof RouteResult.NotFound) {
				releaseUnusedInitialQuota(segments.size(), index, unusedInitialQuotaRelease);
				return RouteTravelTimeResult.verified(providerTravelTimes);
			}
		}
		return RouteTravelTimeResult.verified(providerTravelTimes);
	}

	private void releaseUnusedInitialQuota(
			int segmentCount,
			int attemptedSegmentIndex,
			LongConsumer unusedInitialQuotaRelease
	) {
		long unusedRequestCount = segmentCount - attemptedSegmentIndex - 1L;
		if (unusedRequestCount > 0) {
			unusedInitialQuotaRelease.accept(unusedRequestCount);
		}
	}

	RouteTravelTimeResult fallbackWithHaversine(
			TravelMode travelMode,
			List<RouteSegment> orderedSegments,
			RouteFallbackReason fallbackReason
	) {
		return fallbackWithHaversine(travelMode, orderedSegments, fallbackReason, Set.of());
	}

	RouteTravelTimeResult fallbackWithHaversine(
			TravelMode travelMode,
			List<RouteSegment> orderedSegments,
			RouteFallbackReason fallbackReason,
			Set<UsageDenialScope> quotaDeniedScopes
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(orderedSegments, "orderedSegments must not be null");
		Objects.requireNonNull(fallbackReason, "fallbackReason must not be null");
		List<RouteSegment> segments = List.copyOf(orderedSegments);
		return RouteTravelTimeResult.fallback(
				estimateAllWithHaversine(travelMode, segments),
				fallbackReason,
				quotaDeniedScopes
		);
	}

	private RouteAttempt findRouteWithRetry(
			TravelMode travelMode,
			RouteSegment segment,
			Supplier<UsageReservationResult> retryQuotaReservation
	) {
		try {
			return RouteAttempt.completed(findRoute(travelMode, segment));
		}
		catch (RouteClientException exception) {
			if (!exception.failure().isTransientTechnicalFailure()) {
				throw exception;
			}
			UsageReservationResult reservation = Objects.requireNonNull(
					retryQuotaReservation.get(),
					"retryQuotaReservation result must not be null"
			);
			if (!reservation.acquired()) {
				return RouteAttempt.quotaUnavailable(reservation.deniedScopes());
			}
			return RouteAttempt.completed(findRoute(travelMode, segment));
		}
	}

	private record RouteAttempt(
			RouteResult result,
			boolean retryQuotaUnavailable,
			Set<UsageDenialScope> quotaDeniedScopes
	) {

		private RouteAttempt {
			quotaDeniedScopes = Set.copyOf(quotaDeniedScopes);
		}

		private static RouteAttempt completed(RouteResult result) {
			return new RouteAttempt(
					Objects.requireNonNull(result, "result must not be null"),
					false,
					Set.of()
			);
		}

		private static RouteAttempt quotaUnavailable(Set<UsageDenialScope> deniedScopes) {
			return new RouteAttempt(null, true, deniedScopes);
		}
	}

	private List<RouteSegmentTravelTime> estimateAllWithHaversine(
			TravelMode travelMode,
			List<RouteSegment> segments
	) {
		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(
				TravelTimePolicy.defaultFor(travelMode)
		);
		return segments.stream()
				.map(segment -> RouteSegmentTravelTime.found(estimator.estimateMinutes(
						toCoordinate(segment.origin()),
						toCoordinate(segment.destination())
				)))
				.toList();
	}

	private Coordinate toCoordinate(RouteSegment.Endpoint endpoint) {
		return new Coordinate(endpoint.latitude(), endpoint.longitude());
	}

	private RouteSegmentTravelTime toTravelTime(RouteResult result) {
		return switch (result) {
			case RouteResult.Found found -> RouteSegmentTravelTime.found(roundUpToTenMinutes(
					found.durationSeconds()
			));
			case RouteResult.NotFound ignored -> RouteSegmentTravelTime.notFound();
		};
	}

	private int roundUpToTenMinutes(long durationSeconds) {
		if (durationSeconds == 0L) {
			return 0;
		}
		long roundedUnits = Math.addExact(durationSeconds, ROUNDING_UNIT_SECONDS - 1L)
				/ ROUNDING_UNIT_SECONDS;
		return Math.toIntExact(Math.multiplyExact(roundedUnits, ROUNDING_UNIT_MINUTES));
	}
}
