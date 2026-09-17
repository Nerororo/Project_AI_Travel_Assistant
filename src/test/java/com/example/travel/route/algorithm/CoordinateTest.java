package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CoordinateTest {

	@Test
	void acceptsInclusiveLatitudeAndLongitudeBoundaries() {
		assertThat(new Coordinate(-90.0, -180.0))
				.isEqualTo(new Coordinate(-90.0, -180.0));
		assertThat(new Coordinate(90.0, 180.0))
				.isEqualTo(new Coordinate(90.0, 180.0));
	}

	@Test
	void rejectsLatitudeOutsideWgs84Range() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(-90.000001, 0.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(90.000001, 0.0));
	}

	@Test
	void rejectsLongitudeOutsideWgs84Range() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(0.0, -180.000001));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(0.0, 180.000001));
	}

	@Test
	void rejectsNonFiniteValues() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(Double.NaN, 0.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(0.0, Double.POSITIVE_INFINITY));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new Coordinate(Double.NEGATIVE_INFINITY, 0.0));
	}

	@Test
	void normalizesNegativeZero() {
		Coordinate coordinate = new Coordinate(-0.0, -0.0);

		assertThat(Double.doubleToRawLongBits(coordinate.latitude()))
				.isEqualTo(Double.doubleToRawLongBits(0.0));
		assertThat(Double.doubleToRawLongBits(coordinate.longitude()))
				.isEqualTo(Double.doubleToRawLongBits(0.0));
	}
}
