package com.example.travel.place.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HotelSearchApiRequest(
		@NotBlank String regionId,
		@NotNull @Size(min = 1, max = 35) List<@NotBlank String> attractionSelectionTokens,
		@NotNull Mode mode,
		Integer radiusMeters,
		@Valid Bounds bounds,
		@Min(1) @Max(45) int page,
		@Min(1) @Max(15) int size
) {
	public enum Mode {
		GEOMETRIC_MEDIAN,
		MEDOID,
		MAP_BOUNDS
	}

	public record Bounds(
			@NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double minLatitude,
			@NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double minLongitude,
			@NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double maxLatitude,
			@NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double maxLongitude
	) {
	}
}
