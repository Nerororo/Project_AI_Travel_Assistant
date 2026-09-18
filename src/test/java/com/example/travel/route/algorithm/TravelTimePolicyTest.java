package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TravelTimePolicyTest {

	@Test
	void providesSeparateDefaultsForEachTravelMode() {
		assertThat(TravelTimePolicy.defaultFor(TravelMode.CAR))
				.isEqualTo(new TravelTimePolicy(TravelMode.CAR, 1.30, 60.0));
		assertThat(TravelTimePolicy.defaultFor(TravelMode.PUBLIC_TRANSIT))
				.isEqualTo(new TravelTimePolicy(TravelMode.PUBLIC_TRANSIT, 1.40, 50.0));
	}

	@Test
	void acceptsPolicyBoundaryValues() {
		assertThat(new TravelTimePolicy(TravelMode.CAR, 1.0, 0.1).detourFactor())
				.isEqualTo(1.0);
		assertThat(new TravelTimePolicy(TravelMode.CAR, 3.0, 200.0).averageSpeedKilometersPerHour())
				.isEqualTo(200.0);
	}

	@Test
	void rejectsInvalidCoefficientsAndMode() {
		assertThatNullPointerException()
				.isThrownBy(() -> new TravelTimePolicy(null, 1.3, 60.0));
		assertThatNullPointerException()
				.isThrownBy(() -> TravelTimePolicy.defaultFor(null));

		for (double detourFactor : new double[]{0.999, 3.001, Double.NaN,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> new TravelTimePolicy(TravelMode.CAR, detourFactor, 60.0));
		}
		for (double speed : new double[]{0.0, -1.0, 200.001, Double.NaN,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> new TravelTimePolicy(TravelMode.CAR, 1.3, speed));
		}
	}
}
