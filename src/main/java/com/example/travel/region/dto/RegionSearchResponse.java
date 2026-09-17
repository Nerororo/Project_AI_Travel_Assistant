package com.example.travel.region.dto;

import java.util.List;

public record RegionSearchResponse(List<RegionSearchItem> regions) {

	public RegionSearchResponse {
		regions = List.copyOf(regions);
	}
}
