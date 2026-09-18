package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.CarRouteClient;
import com.example.travel.route.client.PublicTransitRouteClient;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;

import java.util.Objects;

/**
 * Thin route-client boundary. Retry, fallback and rounding policies are added in later route tasks.
 */
public class RouteService {

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
}
