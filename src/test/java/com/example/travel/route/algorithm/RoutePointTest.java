package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RoutePointTest {

	@Test
	void rejectsMissingOrEmptyStableKey() {
		Coordinate coordinate = new Coordinate(0.0, 0.0);

		assertThatNullPointerException()
				.isThrownBy(() -> new RoutePoint(null, coordinate));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new RoutePoint("", coordinate));
	}

	@Test
	void rejectsMissingCoordinate() {
		assertThatNullPointerException()
				.isThrownBy(() -> new RoutePoint("point", null));
	}
}
