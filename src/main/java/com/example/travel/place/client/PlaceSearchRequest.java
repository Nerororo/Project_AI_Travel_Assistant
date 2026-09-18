package com.example.travel.place.client;

import java.util.Objects;

/**
 * Provider-facing place search request. This is not an API request DTO or a persistence model.
 */
public record PlaceSearchRequest(
		String query,
		SearchCenter center,
		Integer radiusMeters,
		int page,
		int size
) {

	private static final int MAX_RADIUS_METERS = 20_000;
	private static final int MAX_PAGE_SIZE = 15;

	public PlaceSearchRequest {
		Objects.requireNonNull(query, "query must not be null");
		query = query.trim();
		if (query.isEmpty()) {
			throw new IllegalArgumentException("query must not be blank");
		}
		if ((center == null) != (radiusMeters == null)) {
			throw new IllegalArgumentException("center and radiusMeters must be provided together");
		}
		if (radiusMeters != null && (radiusMeters < 1 || radiusMeters > MAX_RADIUS_METERS)) {
			throw new IllegalArgumentException("radiusMeters must be between 1 and 20000");
		}
		if (page < 1) {
			throw new IllegalArgumentException("page must be at least 1");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and 15");
		}
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
}
