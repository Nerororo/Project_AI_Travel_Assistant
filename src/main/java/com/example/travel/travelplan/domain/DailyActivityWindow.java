package com.example.travel.travelplan.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * The user-controlled activity range for one travel date.
 */
public record DailyActivityWindow(LocalDate date, LocalTime startTime, LocalTime endTime) {

	public DailyActivityWindow {
		Objects.requireNonNull(date, "date must not be null");
		Objects.requireNonNull(startTime, "startTime must not be null");
		Objects.requireNonNull(endTime, "endTime must not be null");
		if (!startTime.isBefore(endTime)) {
			throw new IllegalArgumentException("activity startTime must be before endTime");
		}
	}
}
