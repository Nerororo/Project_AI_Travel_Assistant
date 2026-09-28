package com.example.travel.travelplan.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public record MealSlot(LocalDate date, MealType mealType, LocalTime startTime, LocalTime endTime) {

	public static final int DURATION_MINUTES = 60;

	public MealSlot {
		Objects.requireNonNull(date, "date must not be null");
		Objects.requireNonNull(mealType, "mealType must not be null");
		Objects.requireNonNull(startTime, "startTime must not be null");
		Objects.requireNonNull(endTime, "endTime must not be null");
		if (!startTime.plusMinutes(DURATION_MINUTES).equals(endTime)) {
			throw new IllegalArgumentException("meal slot must be exactly 60 minutes");
		}
	}
}
