package com.example.travel.travelplan.dto;

import com.example.travel.global.exception.ErrorDetails;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record PlanCapacityDetails(
		LocalDate date,
		@JsonFormat(pattern = "HH:mm") LocalTime plannedEndTime,
		@JsonFormat(pattern = "HH:mm") LocalTime allowedEndTime,
		long exceededMinutes
) implements ErrorDetails {
}
