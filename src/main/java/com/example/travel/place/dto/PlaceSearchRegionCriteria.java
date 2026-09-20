package com.example.travel.place.dto;

import com.example.travel.place.domain.PlaceRole;

/**
 * Region-related fields of a place search request.
 */
public record PlaceSearchRegionCriteria(
		String regionId,
		String districtFilterId,
		PlaceRole placeRole
) {
}
