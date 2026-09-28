package com.example.travel.travelplan.domain;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Places a fixed 60-minute meal inside both the activity and meal windows.
 */
public final class MealSlotPolicy {

	private MealSlotPolicy() {
	}

	public static Optional<MealSlot> createSlot(DailyActivityWindow activityWindow, MealType mealType) {
		Objects.requireNonNull(activityWindow, "activityWindow must not be null");
		Objects.requireNonNull(mealType, "mealType must not be null");

		LocalTime earliestStart = laterOf(activityWindow.startTime(), mealType.allowedStart());
		LocalTime latestEnd = earlierOf(activityWindow.endTime(), mealType.allowedEnd());
		LocalTime latestStart = latestEnd.minusMinutes(MealSlot.DURATION_MINUTES);
		if (earliestStart.isAfter(latestStart)) {
			return Optional.empty();
		}

		LocalTime start = mealType.preferredStart();
		if (start.isBefore(earliestStart)) {
			start = earliestStart;
		}
		else if (start.isAfter(latestStart)) {
			start = latestStart;
		}
		return Optional.of(new MealSlot(activityWindow.date(), mealType, start,
				start.plusMinutes(MealSlot.DURATION_MINUTES)));
	}

	private static LocalTime laterOf(LocalTime left, LocalTime right) {
		return left.isAfter(right) ? left : right;
	}

	private static LocalTime earlierOf(LocalTime left, LocalTime right) {
		return left.isBefore(right) ? left : right;
	}
}
