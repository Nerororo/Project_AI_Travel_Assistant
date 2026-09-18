package com.example.travel.place.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlaceSearchRadiusPolicyTest {

	@Test
	void returnsRoleSpecificRadiiInExpansionOrder() {
		assertThat(PlaceSearchRadiusPolicy.radiiMeters(PlaceRole.ATTRACTION)).containsExactly(20_000);
		assertThat(PlaceSearchRadiusPolicy.radiiMeters(PlaceRole.HOTEL)).containsExactly(5_000, 10_000);
		assertThat(PlaceSearchRadiusPolicy.radiiMeters(PlaceRole.RESTAURANT)).containsExactly(1_000, 3_000, 5_000);
	}

	@Test
	void returnsAnImmutablePolicyAndRejectsMissingRole() {
		assertThat(PlaceSearchRadiusPolicy.radiiMeters(PlaceRole.HOTEL)).isUnmodifiable();
		assertThatNullPointerException().isThrownBy(() -> PlaceSearchRadiusPolicy.radiiMeters(null));
	}
}
