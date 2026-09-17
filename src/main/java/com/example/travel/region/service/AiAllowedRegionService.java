package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.dto.AiAllowedRegion;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiAllowedRegionService {

	private final RegionCatalog catalog;

	public AiAllowedRegionService(RegionCatalog catalog) {
		this.catalog = catalog;
	}

	public List<AiAllowedRegion> findAll() {
		return catalog.regions().stream()
				.filter(Region::selectable)
				.map(region -> new AiAllowedRegion(
						region.regionId(),
						region.name(),
						provinceName(region)))
				.toList();
	}

	public boolean isSelectable(String regionId) {
		return catalog.findById(regionId)
				.filter(Region::selectable)
				.isPresent();
	}

	private String provinceName(Region region) {
		if (region.parentRegionId() == null) {
			return null;
		}
		return catalog.findById(region.parentRegionId())
				.orElseThrow(() -> new IllegalStateException("Validated parent region is missing"))
				.name();
	}
}
