package com.example.travel.region.dto;

import com.example.travel.region.domain.RegionType;

public record RegionSearchItem(
		String regionId,
		String name,
		String shortName,
		String provinceName,
		String parentRegionId,
		RegionType type,
		boolean selectable,
		boolean placeSearchFilterable
) {
}
