package com.example.travel.region.dto;

import java.util.List;

/**
 * Minimal region contract exposed to the place domain for search validation.
 */
public record PlaceSearchRegion(
		String regionId,
		String name,
		String parentRegionId,
		boolean selectable,
		boolean placeSearchFilterable,
		Coordinate representativeCoordinate,
		Bounds searchBounds,
		AddressBoundary addressBoundary
) {
	public PlaceSearchRegion(
			String regionId,
			String parentRegionId,
			boolean selectable,
			boolean placeSearchFilterable
	) {
		this(regionId, null, parentRegionId, selectable, placeSearchFilterable, null, null, null);
	}

	public record Coordinate(double latitude, double longitude) {
	}

	public record Bounds(
			double minLatitude,
			double minLongitude,
			double maxLatitude,
			double maxLongitude
	) {
	}

	public record AddressBoundary(List<String> region1Names, List<String> region2Names) {
		public AddressBoundary {
			region1Names = List.copyOf(region1Names);
			region2Names = List.copyOf(region2Names);
		}
	}
}
