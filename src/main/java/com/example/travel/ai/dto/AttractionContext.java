package com.example.travel.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttractionContext(
		@NotBlank @Size(max = 100) @Pattern(regexp = "\\S+") String clientPlaceId,
		@NotBlank @Size(max = 50) String displayName
) {

	public AttractionContext {
		displayName = displayName == null ? null : displayName.trim();
	}
}
