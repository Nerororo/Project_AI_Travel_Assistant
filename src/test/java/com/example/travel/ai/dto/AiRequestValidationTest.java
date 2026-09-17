package com.example.travel.ai.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		validator = Validation.buildDefaultValidatorFactory().getValidator();
	}

	@Test
	void validatesTrimmedRegionRecommendationRequestLength() {
		var valid = new RegionRecommendationRequest("  " + "가".repeat(500) + "  ");
		var tooLong = new RegionRecommendationRequest("가".repeat(501));
		var blank = new RegionRecommendationRequest("   ");

		assertThat(valid.request()).hasSize(500);
		assertThat(validator.validate(valid)).isEmpty();
		assertThat(validator.validate(tooLong))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("request");
		assertThat(validator.validate(blank))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("request");
	}

	@Test
	void validatesMenuRequestAndNestedAttractionFields() {
		var valid = new MenuAnalysisRequest(
				" region-a ",
				"  " + "나".repeat(200) + "  ",
				List.of(new AttractionContext("place-a", "사용자 장소 A"))
		);
		var invalid = new MenuAnalysisRequest(
				" ",
				"나".repeat(201),
				List.of(new AttractionContext(" ", " "))
		);

		assertThat(valid.regionId()).isEqualTo("region-a");
		assertThat(valid.request()).hasSize(200);
		assertThat(validator.validate(valid)).isEmpty();
		assertThat(validator.validate(invalid))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("regionId", "request", "attractions[0].clientPlaceId", "attractions[0].displayName");
	}
}
