package com.example.travel.place.client;

import java.util.Objects;

/**
 * Provider-facing place search request. This is not an API request DTO or a persistence model.
 */
public record PlaceSearchRequest(
		String query,
		SearchCenter center,
		Integer radiusMeters,
		SearchBounds bounds,
		int page,
		int size
) {

	private static final int MAX_RADIUS_METERS = 20_000;
	private static final int MAX_PAGE = 45;
	private static final int MAX_PAGE_SIZE = 15;

	public PlaceSearchRequest {
		Objects.requireNonNull(query, "query must not be null");
		query = query.trim();
		if (query.isEmpty()) {
			throw new IllegalArgumentException("query must not be blank");
		}
		boolean hasCenterRadiusInput = center != null || radiusMeters != null;
		boolean hasBoundsInput = bounds != null;
		if (hasCenterRadiusInput == hasBoundsInput) {
			throw new IllegalArgumentException("exactly one of center-radius or bounds must be provided");
		}
		if (hasCenterRadiusInput && (center == null || radiusMeters == null)) {
			throw new IllegalArgumentException("center and radiusMeters must be provided together");
		}
		if (radiusMeters != null && (radiusMeters < 0 || radiusMeters > MAX_RADIUS_METERS)) {
			throw new IllegalArgumentException("radiusMeters must be between 0 and 20000");
		}
		if (page < 1 || page > MAX_PAGE) {
			throw new IllegalArgumentException("page must be between 1 and 45");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and 15");
		}
	}

	public static PlaceSearchRequest around(
			String query,
			SearchCenter center,
			int radiusMeters,
			int page,
			int size
	) {
		return new PlaceSearchRequest(query, center, radiusMeters, null, page, size);
	}

	public static PlaceSearchRequest withinBounds(
			String query,
			SearchBounds bounds,
			int page,
			int size
	) {
		return new PlaceSearchRequest(query, null, null, bounds, page, size);
	}

	/**
	 * WGS84 center used only while executing the current search request.
	 */
	public record SearchCenter(double latitude, double longitude) {

		public SearchCenter {
			if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
				throw new IllegalArgumentException("latitude must be finite and between -90 and 90");
			}
			if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
				throw new IllegalArgumentException("longitude must be finite and between -180 and 180");
			}
			latitude = normalizeZero(latitude);
			longitude = normalizeZero(longitude);
		}

		private static double normalizeZero(double value) {
			return value == 0.0 ? 0.0 : value;
		}
	}

	/**
	 * WGS84 rectangle used only while executing the current search request.
	 * A provider adapter serializes it as minLongitude,minLatitude,maxLongitude,maxLatitude.
	 */
	public record SearchBounds(
			double minLatitude,
			double minLongitude,
			double maxLatitude,
			double maxLongitude
	) {

		public SearchBounds {
			if (!isLatitude(minLatitude) || !isLatitude(maxLatitude)) {
				throw new IllegalArgumentException("bounds latitudes must be finite and between -90 and 90");
			}
			if (!isLongitude(minLongitude) || !isLongitude(maxLongitude)) {
				throw new IllegalArgumentException("bounds longitudes must be finite and between -180 and 180");
			}
			if (minLatitude >= maxLatitude || minLongitude >= maxLongitude) {
				throw new IllegalArgumentException("bounds minimums must be less than maximums");
			}
			minLatitude = normalizeZero(minLatitude);
			minLongitude = normalizeZero(minLongitude);
			maxLatitude = normalizeZero(maxLatitude);
			maxLongitude = normalizeZero(maxLongitude);
		}

		private static boolean isLatitude(double value) {
			return Double.isFinite(value) && value >= -90.0 && value <= 90.0;
		}

		private static boolean isLongitude(double value) {
			return Double.isFinite(value) && value >= -180.0 && value <= 180.0;
		}

		private static double normalizeZero(double value) {
			return value == 0.0 ? 0.0 : value;
		}
	}
}
