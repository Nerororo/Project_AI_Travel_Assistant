package com.example.travel.region.domain;

import java.util.List;

public record Region(
		String regionId,
		String name,
		String shortName,
		List<String> aliases,
		String parentRegionId,
		RegionType type,
		boolean selectable,
		boolean placeSearchFilterable,
		RepresentativeCoordinate representativeCoordinate,
		AddressBoundary addressBoundary,
		List<String> sourceRefs,
		String sourceDate
) {

	public Region {
		aliases = aliases == null ? null : List.copyOf(aliases);
		sourceRefs = sourceRefs == null ? null : List.copyOf(sourceRefs);
	}
}
