package com.example.travel.travelplan.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyActivityWindowTest {

	@Test
	void acceptsAnActivityWindowWhenStartIsBeforeEnd() {
		DailyActivityWindow window = new DailyActivityWindow(
				LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), LocalTime.of(20, 0));

		assertThat(window.startTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(window.endTime()).isEqualTo(LocalTime.of(20, 0));
	}

	@Test
	void rejectsEqualOrReversedActivityTimes() {
		LocalDate date = LocalDate.of(2026, 10, 1);
		assertThatThrownBy(() -> new DailyActivityWindow(date, LocalTime.NOON, LocalTime.NOON))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DailyActivityWindow(date, LocalTime.of(20, 0), LocalTime.of(10, 0)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
