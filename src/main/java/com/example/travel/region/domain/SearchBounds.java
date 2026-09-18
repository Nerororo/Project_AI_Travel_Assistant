package com.example.travel.region.domain;

public record SearchBounds(
		double minLatitude,
		double minLongitude,
		double maxLatitude,
		double maxLongitude
) {
}
