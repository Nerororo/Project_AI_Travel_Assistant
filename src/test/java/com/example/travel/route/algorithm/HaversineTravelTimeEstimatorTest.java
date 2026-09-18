package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class HaversineTravelTimeEstimatorTest {

	@Test
	void returnsZeroForTheSameCoordinate() {
		Coordinate coordinate = new Coordinate(37.5665, 126.9780);
		HaversineTravelTimeEstimator estimator = estimator(TravelMode.CAR);

		assertThat(estimator.estimateMinutes(coordinate, coordinate))
				.isZero();
	}

	@Test
	void roundsAnyPositiveTravelTimeUpToAtLeastTenMinutes() {
		TravelTimePolicy policy = new TravelTimePolicy(TravelMode.CAR, 1.0, 60.0);
		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(policy);

		assertThat(estimator.estimateMinutes(0.001))
				.isEqualTo(10);
	}

	@Test
	void preservesExactTenMinuteBoundaryAndRoundsBoundaryExcessUp() {
		TravelTimePolicy policy = new TravelTimePolicy(TravelMode.CAR, 1.0, 60.0);
		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(policy);

		assertThat(estimator.estimateMinutes(10.0))
				.isEqualTo(10);
		assertThat(estimator.estimateMinutes(10.000_001))
				.isEqualTo(20);
	}

	@Test
	void appliesCarAndPublicTransitPoliciesWithoutFixedBuffer() {
		double distanceKilometers = 100.0;

		HaversineTravelTimeEstimator carEstimator = estimator(TravelMode.CAR);
		HaversineTravelTimeEstimator publicTransitEstimator = estimator(TravelMode.PUBLIC_TRANSIT);

		int carMinutes = carEstimator.estimateMinutes(distanceKilometers);
		int publicTransitMinutes = publicTransitEstimator.estimateMinutes(distanceKilometers);

		assertThat(carEstimator.travelMode()).isEqualTo(TravelMode.CAR);
		assertThat(publicTransitEstimator.travelMode()).isEqualTo(TravelMode.PUBLIC_TRANSIT);
		assertThat(carMinutes).isEqualTo(130);
		assertThat(publicTransitMinutes).isEqualTo(170);
	}

	@Test
	void rejectsInvalidDistanceAndNullInputs() {
		HaversineTravelTimeEstimator estimator = estimator(TravelMode.CAR);
		Coordinate coordinate = new Coordinate(37.5665, 126.9780);

		assertThatNullPointerException()
				.isThrownBy(() -> new HaversineTravelTimeEstimator(null));
		assertThatNullPointerException()
				.isThrownBy(() -> estimator.estimateMinutes(null, coordinate));
		assertThatNullPointerException()
				.isThrownBy(() -> estimator.estimateMinutes(coordinate, null));

		for (double distance : new double[]{-0.001, Double.NaN,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> estimator.estimateMinutes(distance));
		}
	}

	private static HaversineTravelTimeEstimator estimator(TravelMode travelMode) {
		return new HaversineTravelTimeEstimator(TravelTimePolicy.defaultFor(travelMode));
	}
}
