package com.example.travel.travelplan.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class MealSlotPolicyTest {

	private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

	@Test
	void usesPreferredLunchAndDinnerStartsWhenAvailable() {
		DailyActivityWindow fullDay = window(10, 0, 21, 0);

		assertThat(MealSlotPolicy.createSlot(fullDay, MealType.LUNCH)).contains(
				new MealSlot(DATE, MealType.LUNCH, LocalTime.NOON, LocalTime.of(13, 0)));
		assertThat(MealSlotPolicy.createSlot(fullDay, MealType.DINNER)).contains(
				new MealSlot(DATE, MealType.DINNER, LocalTime.of(18, 0), LocalTime.of(19, 0)));
	}

	@Test
	void clampsMealStartToTheAvailableOverlap() {
		assertThat(MealSlotPolicy.createSlot(window(11, 30, 12, 30), MealType.LUNCH)).contains(
				new MealSlot(DATE, MealType.LUNCH, LocalTime.of(11, 30), LocalTime.of(12, 30)));
		assertThat(MealSlotPolicy.createSlot(window(13, 0, 14, 0), MealType.LUNCH)).contains(
				new MealSlot(DATE, MealType.LUNCH, LocalTime.of(13, 0), LocalTime.of(14, 0)));
	}

	@Test
	void createsNoSlotWhenTheOverlapIsShorterThanSixtyMinutes() {
		assertThat(MealSlotPolicy.createSlot(window(11, 30, 12, 29), MealType.LUNCH)).isEmpty();
		assertThat(MealSlotPolicy.createSlot(window(14, 0, 17, 30), MealType.LUNCH)).isEmpty();
	}

	@Test
	void mealWindowsHaveTheDocumentedBoundaries() {
		assertThat(MealType.LUNCH.allowedStart()).isEqualTo(LocalTime.of(11, 30));
		assertThat(MealType.LUNCH.allowedEnd()).isEqualTo(LocalTime.of(14, 0));
		assertThat(MealType.DINNER.allowedStart()).isEqualTo(LocalTime.of(17, 30));
		assertThat(MealType.DINNER.allowedEnd()).isEqualTo(LocalTime.of(20, 30));
		assertThat(MealSlot.DURATION_MINUTES).isEqualTo(60);
	}

	private DailyActivityWindow window(int startHour, int startMinute, int endHour, int endMinute) {
		return new DailyActivityWindow(DATE, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
	}
}
