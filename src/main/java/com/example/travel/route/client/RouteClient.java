package com.example.travel.route.client;

/**
 * Contract for looking up one adjacent route segment.
 */
public interface RouteClient {

	RouteResult findRoute(RouteSegment segment);
}
