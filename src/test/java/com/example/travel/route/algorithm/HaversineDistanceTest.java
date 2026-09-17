package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class HaversineDistanceTest {

	private static final double DISTANCE_TOLERANCE_KILOMETERS = 0.001;

	@Test
	void returnsExactZeroForSameCoordinate() {
		Coordinate coordinate = new Coordinate(37.0, 127.0);

		assertThat(HaversineDistance.kilometers(coordinate, coordinate)).isZero();
		assertThat(HaversineDistance.kilometers(
				new Coordinate(37.0, 127.0),
				new Coordinate(37.0, 127.0))).isZero();
	}

	@Test
	void returnsSameDistanceInBothDirections() {
		Coordinate first = new Coordinate(37.0, 127.0);
		Coordinate second = new Coordinate(37.01, 127.02);

		double forward = HaversineDistance.kilometers(first, second);
		double backward = HaversineDistance.kilometers(second, first);

		assertThat(forward).isEqualTo(backward);
	}

	@Test
	void calculatesKnownShortDistanceInKilometers() {
		Coordinate first = new Coordinate(0.0, 0.0);
		Coordinate second = new Coordinate(0.0, 0.01);

		double distance = HaversineDistance.kilometers(first, second);

		assertThat(distance).isCloseTo(1.111951, within(DISTANCE_TOLERANCE_KILOMETERS));
	}

	@Test
	void remainsFiniteForAntipodalCoordinates() {
		double distance = HaversineDistance.kilometers(
				new Coordinate(0.0, 0.0),
				new Coordinate(0.0, 180.0));

		assertThat(distance).isFinite().isPositive();
	}

	@Test
	void rejectsMissingCoordinates() {
		Coordinate coordinate = new Coordinate(37.0, 127.0);

		assertThatNullPointerException()
				.isThrownBy(() -> HaversineDistance.kilometers(null, coordinate));
		assertThatNullPointerException()
				.isThrownBy(() -> HaversineDistance.kilometers(coordinate, null));
	}

	private static org.assertj.core.data.Offset<Double> within(double tolerance) {
		return org.assertj.core.data.Offset.offset(tolerance);
	}
}
