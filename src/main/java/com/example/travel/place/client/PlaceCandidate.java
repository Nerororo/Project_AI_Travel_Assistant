package com.example.travel.place.client;

import java.net.URI;
import java.util.Objects;

/**
 * Request-scoped fields mapped from a Kakao Local response.
 *
 * <p>This type intentionally lives in {@code place.client}, separately from API DTOs and future
 * persistence models. Provider display name, address, coordinates and category must be discarded
 * after the current creation request and must never be persisted, cached or logged.</p>
 */
public record PlaceCandidate(
		String kakaoPlaceId,
		URI placeUrl,
		String providerDisplayName,
		String address,
		double latitude,
		double longitude,
		String providerCategory
) {

	public PlaceCandidate {
		kakaoPlaceId = requireText(kakaoPlaceId, "kakaoPlaceId");
		Objects.requireNonNull(placeUrl, "placeUrl must not be null");
		if (!"https".equalsIgnoreCase(placeUrl.getScheme())) {
			throw new IllegalArgumentException("placeUrl must use https");
		}
		providerDisplayName = requireText(providerDisplayName, "providerDisplayName");
		address = requireText(address, "address");
		providerCategory = requireText(providerCategory, "providerCategory");
		if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
			throw new IllegalArgumentException("latitude must be finite and between -90 and 90");
		}
		if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
			throw new IllegalArgumentException("longitude must be finite and between -180 and 180");
		}
		latitude = normalizeZero(latitude);
		longitude = normalizeZero(longitude);
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name + " must not be null");
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return trimmed;
	}

	private static double normalizeZero(double value) {
		return value == 0.0 ? 0.0 : value;
	}
}
