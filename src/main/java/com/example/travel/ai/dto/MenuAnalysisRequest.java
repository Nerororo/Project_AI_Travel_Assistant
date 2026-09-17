package com.example.travel.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MenuAnalysisRequest(
		@NotBlank String regionId,
		@NotBlank @Size(max = 200) String request,
		@NotNull @Size(max = 35) @Valid List<AttractionContext> attractions
) {

	public MenuAnalysisRequest {
		request = request == null ? null : request.trim();
		attractions = attractions == null ? null : List.copyOf(attractions);
	}
}
