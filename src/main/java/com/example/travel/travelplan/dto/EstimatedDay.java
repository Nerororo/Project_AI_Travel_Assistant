package com.example.travel.travelplan.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EstimatedDay(LocalDate date, LocalDateTime plannedEndTime, List<EstimatedItem> items) {
	public EstimatedDay {
		items = List.copyOf(items);
	}
}
