package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.TravelPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelConditionsTest {

	private static final LocalDate OCTOBER_1 = LocalDate.of(2026, 10, 1);
	private static final TravelPeriod TWO_DAYS = new TravelPeriod(OCTOBER_1, OCTOBER_1.plusDays(1));

	@Test
	void acceptsExactlyOneActivityWindowForEveryTravelDate() {
		TravelConditions conditions = TravelConditions.withDefaultMealTravelBuffer(
				TWO_DAYS,
				TravelMode.CAR,
				List.of(day(OCTOBER_1), day(OCTOBER_1.plusDays(1))));

		assertThat(conditions.travelMode()).isEqualTo(TravelMode.CAR);
		assertThat(conditions.mealTravelBufferMinutes()).isEqualTo(15);
		assertThat(conditions.days()).hasSize(2);
	}

	@Test
	void rejectsMissingDuplicatedAndOutOfPeriodActivityDates() {
		assertThatThrownBy(() -> conditions(List.of(day(OCTOBER_1))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> conditions(List.of(day(OCTOBER_1), day(OCTOBER_1))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> conditions(List.of(day(OCTOBER_1), day(OCTOBER_1.plusDays(2)))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void keepsAnImmutableCopyOfActivityWindows() {
		List<DailyActivityWindow> mutableDays = new ArrayList<>(List.of(
				day(OCTOBER_1), day(OCTOBER_1.plusDays(1))));
		TravelConditions conditions = conditions(mutableDays);

		mutableDays.clear();

		assertThat(conditions.days()).hasSize(2);
		assertThatThrownBy(() -> conditions.days().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void validatesExplicitMealTravelBuffer() {
		assertThat(new TravelConditions(TWO_DAYS, TravelMode.PUBLIC_TRANSIT,
				List.of(day(OCTOBER_1), day(OCTOBER_1.plusDays(1))), 60).mealTravelBufferMinutes())
				.isEqualTo(60);
		assertThatThrownBy(() -> new TravelConditions(TWO_DAYS, TravelMode.PUBLIC_TRANSIT,
				List.of(day(OCTOBER_1), day(OCTOBER_1.plusDays(1))), 61))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private TravelConditions conditions(List<DailyActivityWindow> days) {
		return new TravelConditions(TWO_DAYS, TravelMode.CAR, days, 15);
	}

	private DailyActivityWindow day(LocalDate date) {
		return new DailyActivityWindow(date, LocalTime.of(10, 0), LocalTime.of(20, 0));
	}
}
