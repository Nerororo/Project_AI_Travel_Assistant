package com.example.travel.travelplan.dto;

import com.example.travel.global.exception.ErrorDetails;

import java.time.LocalDate;
import java.time.LocalTime;

public record PlanCapacityDetails(
		LocalDate date,
		LocalTime plannedEndTime,
		LocalTime allowedEndTime,
		long exceededMinutes
) implements ErrorDetails {
}
