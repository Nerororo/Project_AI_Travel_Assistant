package com.example.travel.travelplan.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelPeriodTest {

	@Test
	void acceptsInclusivePeriodsFromOneToSevenDays() {
		TravelPeriod oneDay = new TravelPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
		TravelPeriod sevenDays = new TravelPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));

		assertThat(oneDay.dayCount()).isEqualTo(1);
		assertThat(sevenDays.dayCount()).isEqualTo(7);
		assertThat(sevenDays.dates()).containsExactly(
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
				LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
				LocalDate.of(2026, 10, 7));
	}

	@Test
	void rejectsReversedAndEightDayPeriods() {
		assertThatThrownBy(() -> new TravelPeriod(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TravelPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void reportsWhetherADateBelongsToThePeriod() {
		TravelPeriod period = new TravelPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));

		assertThat(period.contains(LocalDate.of(2026, 10, 1))).isTrue();
		assertThat(period.contains(LocalDate.of(2026, 10, 3))).isTrue();
		assertThat(period.contains(LocalDate.of(2026, 10, 4))).isFalse();
		assertThatNullPointerException().isThrownBy(() -> period.contains(null));
	}
}
