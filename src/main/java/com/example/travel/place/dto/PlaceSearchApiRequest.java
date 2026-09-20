package com.example.travel.place.dto;

import com.example.travel.place.domain.PlaceRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceSearchApiRequest(
		@NotBlank @Size(max = 100) String regionId,
		@Size(max = 100) String districtFilterId,
		@NotNull PlaceRole placeRole,
		@NotBlank @Size(max = 100) String query,
		@Valid Center center,
		@Min(0) @Max(20_000) Integer radiusMeters,
		@Min(1) @Max(45) int page,
		@Min(1) @Max(15) int size
) {

	public PlaceSearchApiRequest {
		query = query == null ? null : query.trim();
	}

	public record Center(
			@NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double latitude,
			@NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double longitude
	) {
	}
}
