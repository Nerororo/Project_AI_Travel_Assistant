package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageReservationLease;
import com.example.travel.user.dto.UsageReservationResult;
import com.example.travel.user.service.ApiUsageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.time.Instant;

/**
 * Counts provider requests for an ordered route candidate and reserves their quota before routing.
 */
@Service
public class RouteQuotaService {

	private final ApiUsageService apiUsageService;

	public RouteQuotaService(ApiUsageService apiUsageService) {
		this.apiUsageService = Objects.requireNonNull(apiUsageService, "apiUsageService must not be null");
	}

	public UsageReservationResult tryAcquire(
			long userId,
			TravelMode travelMode,
			List<RouteSegment> orderedSegments
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		Objects.requireNonNull(orderedSegments, "orderedSegments must not be null");
		List<RouteSegment> segments = List.copyOf(orderedSegments);
		return tryAcquire(userId, travelMode, segments.size());
	}

	public UsageReservationLease reserve(
			long userId,
			TravelMode travelMode,
			List<RouteSegment> orderedSegments
	) {
		Objects.requireNonNull(orderedSegments, "orderedSegments must not be null");
		List<RouteSegment> segments = List.copyOf(orderedSegments);
		return reserve(userId, travelMode, segments.size());
	}

	public UsageReservationResult tryAcquire(
			long userId,
			TravelMode travelMode,
			long requestCount
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		if (requestCount < 0) {
			throw new IllegalArgumentException("requestCount must not be negative");
		}
		if (requestCount == 0) {
			return UsageReservationResult.success();
		}

		return apiUsageService.tryAcquire(userId, usageFeature(travelMode), requestCount);
	}

	public UsageReservationLease reserve(long userId, TravelMode travelMode, long requestCount) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		if (requestCount < 0) {
			throw new IllegalArgumentException("requestCount must not be negative");
		}
		if (requestCount == 0) {
			return UsageReservationLease.acquired(Instant.EPOCH);
		}
		return apiUsageService.reserve(userId, usageFeature(travelMode), requestCount);
	}

	public void release(
			long userId,
			TravelMode travelMode,
			UsageReservationLease reservation,
			long requestCount
	) {
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		if (requestCount <= 0) {
			throw new IllegalArgumentException("requestCount must be positive");
		}
		apiUsageService.release(userId, usageFeature(travelMode), reservation, requestCount);
	}

	private UsageFeature usageFeature(TravelMode travelMode) {
		return switch (travelMode) {
			case CAR -> UsageFeature.CAR_ROUTE;
			case PUBLIC_TRANSIT -> UsageFeature.PUBLIC_TRANSIT_ROUTE;
		};
	}
}
