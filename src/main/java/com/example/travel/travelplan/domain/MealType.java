package com.example.travel.travelplan.domain;

import java.time.LocalTime;

public enum MealType {
	LUNCH(LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(12, 0)),
	DINNER(LocalTime.of(17, 30), LocalTime.of(20, 30), LocalTime.of(18, 0));

	private final LocalTime allowedStart;
	private final LocalTime allowedEnd;
	private final LocalTime preferredStart;

	MealType(LocalTime allowedStart, LocalTime allowedEnd, LocalTime preferredStart) {
		this.allowedStart = allowedStart;
		this.allowedEnd = allowedEnd;
		this.preferredStart = preferredStart;
	}

	public LocalTime allowedStart() {
		return allowedStart;
	}

	public LocalTime allowedEnd() {
		return allowedEnd;
	}

	public LocalTime preferredStart() {
		return preferredStart;
	}
}
