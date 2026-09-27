package com.example.travel.route.service;

/**
 * Privacy-safe aggregate outcome for the metrics implementation introduced in Q1-04.
 */
public enum RouteObservationOutcome {
	PROVIDER_VERIFIED,
	QUOTA_FALLBACK,
	TECHNICAL_FAILURE_FALLBACK
}
