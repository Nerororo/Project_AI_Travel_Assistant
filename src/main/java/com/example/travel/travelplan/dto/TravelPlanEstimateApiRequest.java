package com.example.travel.travelplan.dto;

import com.example.travel.route.algorithm.TravelMode;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** HTTP input. Selection tokens are resolved before building the calculation command. */
public record TravelPlanEstimateApiRequest(
		@NotBlank String regionId,
		@NotNull TravelMode travelMode,
		@NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
		@NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
		@NotBlank String startBoundarySelectionToken,
		@NotBlank String endBoundarySelectionToken,
		@NotNull @Size(min = 1, max = 7) List<@Valid Day> days,
		@NotNull List<@Valid Place> places,
		String hotelSelectionToken,
		Integer mealTravelBufferMinutes,
		@NotNull @Size(min = 1, max = 5) List<String> foods
) {
	public record Day(
			@NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
			@NotNull @JsonFormat(pattern = "HH:mm") LocalTime activityStartTime,
			@NotNull @JsonFormat(pattern = "HH:mm") LocalTime activityEndTime
	) {
	}

	public record Place(
			@NotNull UUID clientPlaceId,
			@NotBlank String selectionToken,
			@NotBlank String displayName,
			@NotNull Integer stayMinutes,
			@JsonFormat(pattern = "yyyy-MM-dd") LocalDate day,
			Integer order
	) {
	}
}
