package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MedoidSelectorTest {

	@Test
	void returnsTheOnlyPoint() {
		RoutePoint only = point("only", 37.5, 127.0);

		assertThat(MedoidSelector.select(List.of(only))).isSameAs(only);
	}

	@Test
	void selectsInputPointWithSmallestTotalDistance() {
		RoutePoint west = point("west", 37.5, 126.8);
		RoutePoint center = point("center", 37.5, 127.0);
		RoutePoint east = point("east", 37.5, 127.5);

		assertThat(MedoidSelector.select(List.of(east, west, center))).isSameAs(center);
	}

	@Test
	void resolvesDistanceTieByStableKeyRegardlessOfInputOrder() {
		RoutePoint alpha = point("alpha", 37.5, 126.9);
		RoutePoint beta = point("beta", 37.5, 127.1);

		assertThat(MedoidSelector.select(List.of(beta, alpha))).isSameAs(alpha);
		assertThat(MedoidSelector.select(List.of(alpha, beta))).isSameAs(alpha);
	}

	@Test
	void permitsDuplicateCoordinatesWithDifferentKeysAndUsesStableKeyTieBreak() {
		RoutePoint beta = point("beta", 37.5, 127.0);
		RoutePoint alpha = point("alpha", 37.5, 127.0);
		RoutePoint distant = point("distant", 37.7, 127.2);

		assertThat(MedoidSelector.select(List.of(beta, distant, alpha))).isSameAs(alpha);
	}

	@Test
	void rejectsEmptyNullAndDuplicateKeyInputs() {
		RoutePoint duplicateA = point("duplicate", 37.5, 127.0);
		RoutePoint duplicateB = point("duplicate", 37.6, 127.1);

		assertThatNullPointerException().isThrownBy(() -> MedoidSelector.select(null));
		assertThatNullPointerException()
				.isThrownBy(() -> MedoidSelector.select(Arrays.asList(duplicateA, null)));
		assertThatIllegalArgumentException().isThrownBy(() -> MedoidSelector.select(List.of()));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> MedoidSelector.select(List.of(duplicateA, duplicateB)));
	}

	private static RoutePoint point(String key, double latitude, double longitude) {
		return new RoutePoint(key, new Coordinate(latitude, longitude));
	}
}
