package com.example.travel.ai.dto;

import java.util.List;

public record RegionRecommendationResponse(List<RecommendedRegion> regions) {

	public RegionRecommendationResponse {
		regions = List.copyOf(regions);
	}

	public record RecommendedRegion(
			String regionId,
			String name,
			String provinceName,
			String reason
	) {
	}
}
