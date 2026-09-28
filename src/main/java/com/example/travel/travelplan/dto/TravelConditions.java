package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.domain.TravelPlanInputPolicy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validated, request-scoped conditions shared by itinerary calculation steps.
 */
public record TravelConditions(
		TravelPeriod period,
		TravelMode travelMode,
		List<DailyActivityWindow> days,
		int mealTravelBufferMinutes
) {

	public TravelConditions {
		Objects.requireNonNull(period, "period must not be null");
		Objects.requireNonNull(travelMode, "travelMode must not be null");
		days = List.copyOf(Objects.requireNonNull(days, "days must not be null"));
		TravelPlanInputPolicy.validateMealTravelBufferMinutes(mealTravelBufferMinutes);
		validateDays(period, days);
	}

	public static TravelConditions withDefaultMealTravelBuffer(
			TravelPeriod period,
			TravelMode travelMode,
			List<DailyActivityWindow> days
	) {
		return new TravelConditions(period, travelMode, days,
				TravelPlanInputPolicy.DEFAULT_MEAL_TRAVEL_BUFFER_MINUTES);
	}

	private static void validateDays(TravelPeriod period, List<DailyActivityWindow> days) {
		Set<LocalDate> actualDates = new HashSet<>();
		for (DailyActivityWindow day : days) {
			Objects.requireNonNull(day, "days must not contain null");
			if (!period.contains(day.date())) {
				throw new IllegalArgumentException("activity date must belong to travel period");
			}
			if (!actualDates.add(day.date())) {
				throw new IllegalArgumentException("activity date must not be duplicated");
			}
		}
		if (!actualDates.equals(new HashSet<>(period.dates()))) {
			throw new IllegalArgumentException("every travel date must have one activity window");
		}
	}
}
