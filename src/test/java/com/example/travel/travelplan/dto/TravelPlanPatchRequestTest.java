package com.example.travel.travelplan.dto;

import com.example.travel.global.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelPlanPatchRequestTest {
	@Test
	void boundsPlaceEditsBeforeIteratingThem() {
		Map<String, Object> edit = Map.of("planPlaceId", 1, "displayName", "place");
		assertThat(TravelPlanPatchRequest.from(Map.of("placeEdits", Collections.nCopies(50, edit)))
				.placeEdits()).hasSize(50);
		assertThatThrownBy(() -> TravelPlanPatchRequest.from(
				Map.of("placeEdits", Collections.nCopies(51, edit))))
				.isInstanceOf(ApiException.class);
	}
}
