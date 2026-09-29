package com.example.travel.travelplan.dto;

import java.util.List;

public record EstimateResult(boolean routeVerified, List<EstimatedDay> days) {
	public EstimateResult {
		days = List.copyOf(days);
	}
}
