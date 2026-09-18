package com.example.travel.place.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Converts a request-scoped provider category into a suggested duration.
 * The provider category and the internal classification are not part of the result.
 */
public final class StayDurationPolicy {

	public static final int MIN_ATTRACTION_MINUTES = 30;
	public static final int MAX_ATTRACTION_MINUTES = 480;
	public static final int ADJUSTMENT_UNIT_MINUTES = 10;
	public static final int RESTAURANT_MINUTES = 60;

	private StayDurationPolicy() {
	}

	public static OptionalInt suggestedMinutes(PlaceRole role, String providerCategory) {
		Objects.requireNonNull(role, "role must not be null");
		return switch (role) {
			case ATTRACTION -> OptionalInt.of(classify(providerCategory).minutes);
			case RESTAURANT -> OptionalInt.of(RESTAURANT_MINUTES);
			case HOTEL -> OptionalInt.empty();
		};
	}

	public static int validateAttractionAdjustment(int stayMinutes) {
		if (stayMinutes < MIN_ATTRACTION_MINUTES || stayMinutes > MAX_ATTRACTION_MINUTES) {
			throw new IllegalArgumentException("stayMinutes must be between 30 and 480");
		}
		if (stayMinutes % ADJUSTMENT_UNIT_MINUTES != 0) {
			throw new IllegalArgumentException("stayMinutes must be a multiple of 10");
		}
		return stayMinutes;
	}

	private static StayKind classify(String providerCategory) {
		Objects.requireNonNull(providerCategory, "providerCategory must not be null");
		String normalized = providerCategory.trim().toLowerCase(Locale.ROOT);
		if (containsAny(normalized, "테마파크", "놀이공원")) {
			return StayKind.THEME_PARK;
		}
		if (containsAny(normalized, "등산", "산악", "트레킹")) {
			return StayKind.HIKING;
		}
		if (containsAny(normalized, "체험", "공방")) {
			return StayKind.EXPERIENCE;
		}
		if (containsAny(normalized, "박물관", "미술관", "전시")) {
			return StayKind.MUSEUM_OR_EXHIBITION;
		}
		return StayKind.GENERAL_OR_NATURE;
	}

	private static boolean containsAny(String value, String... keywords) {
		for (String keyword : keywords) {
			if (value.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private enum StayKind {
		GENERAL_OR_NATURE(90),
		MUSEUM_OR_EXHIBITION(120),
		EXPERIENCE(180),
		HIKING(240),
		THEME_PARK(360);

		private final int minutes;

		StayKind(int minutes) {
			this.minutes = minutes;
		}
	}
}
