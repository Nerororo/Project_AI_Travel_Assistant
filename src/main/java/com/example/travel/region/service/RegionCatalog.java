package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.loader.RegionDataLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RegionCatalog {

	private static final String REGION_DATA_PATH = "data/regions.json";

	private final List<Region> regions;
	private final Map<String, Region> regionsById;

	public RegionCatalog(RegionDataLoader loader) {
		this.regions = loader.load(new ClassPathResource(REGION_DATA_PATH));
		Map<String, Region> index = new LinkedHashMap<>();
		for (Region region : regions) {
			index.put(region.regionId(), region);
		}
		this.regionsById = Map.copyOf(index);
	}

	public List<Region> regions() {
		return regions;
	}

	public Optional<Region> findById(String regionId) {
		return Optional.ofNullable(regionsById.get(regionId));
	}
}
