package com.example.travel.region.service;

import com.example.travel.region.dto.PlaceSearchRegion;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Public read-only boundary used by the place domain.
 */
@Service
public class PlaceSearchRegionService {

	private final RegionCatalog catalog;

	public PlaceSearchRegionService(RegionCatalog catalog) {
		this.catalog = catalog;
	}

	public Optional<PlaceSearchRegion> findById(String regionId) {
		return catalog.findById(regionId)
				.map(region -> new PlaceSearchRegion(
						region.regionId(),
						region.parentRegionId(),
						region.selectable(),
						region.placeSearchFilterable()
				));
	}
}
