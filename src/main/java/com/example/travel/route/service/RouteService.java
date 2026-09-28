package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.CarRouteClient;
import com.example.travel.route.client.PublicTransitRouteClient;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;

import java.util.List;
import java.util.Objects;

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

	public RouteResult findRoute(TravelMode travelMode, RouteSegment segment) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(segment, "segment must not be null");

		return switch (travelMode) {
			case CAR -> carRouteClient.findRoute(segment);
			case PUBLIC_TRANSIT -> publicTransitRouteClient.findRoute(segment);
		};
	}

	/**
	 * Keeps segment order and rounds each provider duration up to ten-minute units.
	 * Retry and fallback remain the responsibility of the later route integration policy.
	 */
	public List<RouteSegmentTravelTime> findEstimatedTravelTimes(
			TravelMode travelMode,
			List<RouteSegment> orderedSegments
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(orderedSegments, "orderedSegments must not be null");
		List<RouteSegment> segments = List.copyOf(orderedSegments);

		return segments.stream()
				.map(segment -> toTravelTime(findRoute(travelMode, segment)))
				.toList();
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
