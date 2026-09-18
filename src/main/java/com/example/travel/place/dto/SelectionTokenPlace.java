package com.example.travel.place.dto;

import com.example.travel.place.domain.PlaceRole;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provider place fields that may exist only in the current creation request.
 */
public record SelectionTokenPlace(
		long userId,
		String regionId,
		PlaceRole placeRole,
		String kakaoPlaceId,
		URI placeUrl,
		double latitude,
		double longitude
) {

	private static final Pattern REGION_ID = Pattern.compile("KR-[0-9]{2,10}");
	private static final Pattern KAKAO_PLACE_ID = Pattern.compile("[0-9]{1,30}");

	public SelectionTokenPlace {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be positive");
		}
		if (regionId == null || !REGION_ID.matcher(regionId).matches()) {
			throw new IllegalArgumentException("regionId must use the supported format");
		}
		Objects.requireNonNull(placeRole, "placeRole must not be null");
		if (kakaoPlaceId == null || !KAKAO_PLACE_ID.matcher(kakaoPlaceId).matches()) {
			throw new IllegalArgumentException("kakaoPlaceId must contain only digits");
		}
		Objects.requireNonNull(placeUrl, "placeUrl must not be null");
		URI expectedUrl = URI.create("https://place.map.kakao.com/" + kakaoPlaceId);
		if (!expectedUrl.equals(placeUrl)) {
			throw new IllegalArgumentException("placeUrl must match kakaoPlaceId");
		}
		if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0) {
			throw new IllegalArgumentException("latitude must be inside the supported Korea range");
		}
		if (!Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
			throw new IllegalArgumentException("longitude must be inside the supported Korea range");
		}
		latitude = normalizeZero(latitude);
		longitude = normalizeZero(longitude);
	}

	private static double normalizeZero(double value) {
		return value == 0.0 ? 0.0 : value;
	}
}
