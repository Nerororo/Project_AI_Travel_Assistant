package com.example.travel.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegionRecommendationRequest(
		@NotBlank @Size(max = 500) String request
) {

	public RegionRecommendationRequest {
		request = request == null ? null : request.trim();
	}
}
