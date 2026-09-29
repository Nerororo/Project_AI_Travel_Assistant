package com.example.travel.travelplan.dto;

import com.example.travel.travelplan.domain.MealType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record RestaurantSearchApiRequest(
		@NotNull @Valid TravelPlanEstimateApiRequest estimate,
		@NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate mealDate,
		@NotNull MealType mealType,
		@NotBlank @Size(max = 50) String menuQuery,
		UUID referenceAttractionClientPlaceId,
		@Valid Bounds bounds,
		@Min(1) @Max(45) int page,
		@Min(1) @Max(15) int size
) {
	public record Bounds(
			@NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double minLatitude,
			@NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double minLongitude,
			@NotNull @DecimalMin("33.0") @DecimalMax("39.0") Double maxLatitude,
			@NotNull @DecimalMin("124.0") @DecimalMax("132.0") Double maxLongitude
	) {
	}
}
