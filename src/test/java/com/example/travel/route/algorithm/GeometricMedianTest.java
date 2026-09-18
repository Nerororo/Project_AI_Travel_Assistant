package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GeometricMedianTest {

	private static final double COORDINATE_TOLERANCE = 1e-8;

	@Test
	void returnsTheOnlyCoordinateUnchanged() {
		Coordinate only = new Coordinate(37.5665, 126.9780);

		assertThat(GeometricMedian.calculate(List.of(only))).isSameAs(only);
	}

	@Test
	void findsCenterOfSymmetricCoordinates() {
		List<Coordinate> coordinates = List.of(
				new Coordinate(37.4, 126.9),
				new Coordinate(37.4, 127.1),
				new Coordinate(37.6, 126.9),
				new Coordinate(37.6, 127.1));

		Coordinate median = GeometricMedian.calculate(coordinates);

		assertThat(median.latitude()).isCloseTo(37.5, within(COORDINATE_TOLERANCE));
		assertThat(median.longitude()).isCloseTo(127.0, within(COORDINATE_TOLERANCE));
	}

	@Test
	void handlesDuplicateCoordinatesWithModifiedWeiszfeldRule() {
		Coordinate duplicate = new Coordinate(37.5, 127.0);
		List<Coordinate> coordinates = List.of(
				duplicate,
				duplicate,
				duplicate,
				new Coordinate(37.7, 127.2));

		Coordinate median = GeometricMedian.calculate(coordinates);

		assertThat(median.latitude()).isCloseTo(duplicate.latitude(), within(COORDINATE_TOLERANCE));
		assertThat(median.longitude()).isCloseTo(duplicate.longitude(), within(COORDINATE_TOLERANCE));
	}

	@Test
	void producesTheSameResultRegardlessOfInputOrder() {
		Coordinate first = new Coordinate(35.1, 126.8);
		Coordinate second = new Coordinate(35.2, 126.9);
		Coordinate third = new Coordinate(35.3, 126.7);

		Coordinate ordered = GeometricMedian.calculate(List.of(first, second, third));
		Coordinate reversed = GeometricMedian.calculate(List.of(third, second, first));

		assertThat(reversed).isEqualTo(ordered);
	}

	@Test
	void returnsFiniteCoordinateAtConfiguredIterationLimit() {
		List<Coordinate> coordinates = List.of(
				new Coordinate(33.1, 126.1),
				new Coordinate(34.2, 127.4),
				new Coordinate(37.8, 128.7));

		Coordinate result = GeometricMedian.calculate(coordinates, 1);

		assertThat(result.latitude()).isFinite();
		assertThat(result.longitude()).isFinite();
	}

	@Test
	void rejectsEmptyNullAndInvalidIterationInputs() {
		Coordinate coordinate = new Coordinate(37.5, 127.0);

		assertThatNullPointerException().isThrownBy(() -> GeometricMedian.calculate(null));
		assertThatNullPointerException()
				.isThrownBy(() -> GeometricMedian.calculate(Arrays.asList(coordinate, null)));
		assertThatIllegalArgumentException().isThrownBy(() -> GeometricMedian.calculate(List.of()));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> GeometricMedian.calculate(List.of(coordinate), 0));
	}

	private static org.assertj.core.data.Offset<Double> within(double value) {
		return org.assertj.core.data.Offset.offset(value);
	}
}
