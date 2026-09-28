package com.example.travel.travelplan.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Inclusive travel dates accepted by an itinerary calculation.
 */
public record TravelPeriod(LocalDate startDate, LocalDate endDate) {

	public static final int MIN_DAYS = 1;
	public static final int MAX_DAYS = 7;

	public TravelPeriod {
		Objects.requireNonNull(startDate, "startDate must not be null");
		Objects.requireNonNull(endDate, "endDate must not be null");
		long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		if (days < MIN_DAYS || days > MAX_DAYS) {
			throw new IllegalArgumentException("travel period must be between 1 and 7 days");
		}
	}

	public int dayCount() {
		return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
	}

	public List<LocalDate> dates() {
		return startDate.datesUntil(endDate.plusDays(1)).toList();
	}

	public boolean contains(LocalDate date) {
		Objects.requireNonNull(date, "date must not be null");
		return !date.isBefore(startDate) && !date.isAfter(endDate);
	}
}
