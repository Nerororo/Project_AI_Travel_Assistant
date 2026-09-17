package com.example.travel.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AttractionContext(
		@NotBlank String clientPlaceId,
		@NotBlank String displayName
) {
}
