package com.example.travel.region.dto;

/**
 * Minimal region contract exposed to the place domain for search validation.
 */
public record PlaceSearchRegion(
		String regionId,
		String parentRegionId,
		boolean selectable,
		boolean placeSearchFilterable
) {
}
