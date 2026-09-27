package com.example.travel.route.service;

/**
 * Request-scoped fallback reason. It must not be persisted with a completed itinerary.
 */
public enum RouteFallbackReason {
	NONE,
	QUOTA_UNAVAILABLE,
	TECHNICAL_FAILURE
}
