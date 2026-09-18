package com.example.travel.region.loader;

import com.example.travel.region.domain.AddressBoundary;
import com.example.travel.region.domain.Region;
import com.example.travel.region.domain.RepresentativeCoordinate;
import com.example.travel.region.domain.SearchBounds;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RegionDataLoader {

	private static final int SUPPORTED_SCHEMA_VERSION = 1;

	private final ObjectMapper objectMapper;

	public RegionDataLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<Region> load(Resource resource) {
		RegionDataFile dataFile;
		try (var inputStream = resource.getInputStream()) {
			dataFile = objectMapper.readValue(inputStream, RegionDataFile.class);
		}
		catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Failed to read region data", exception);
		}

		validateDataFile(dataFile);
		return List.copyOf(dataFile.regions());
	}

	private void validateDataFile(RegionDataFile dataFile) {
		if (dataFile == null) {
			throw invalid("root must not be null");
		}
		if (dataFile.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
			throw invalid("unsupported schemaVersion");
		}
		requireText(dataFile.generatedAt(), "generatedAt");
		if (dataFile.sources() == null || dataFile.sources().isEmpty()) {
			throw invalid("sources must not be empty");
		}
		if (dataFile.regions() == null || dataFile.regions().isEmpty()) {
			throw invalid("regions must not be empty");
		}

		Set<String> sourceIds = validateSources(dataFile.sources());
		Map<String, Region> regionsById = new HashMap<>();
		for (Region region : dataFile.regions()) {
			validateRegion(region, sourceIds);
			if (regionsById.putIfAbsent(region.regionId(), region) != null) {
				throw invalid("duplicate regionId: " + region.regionId());
			}
		}

		validateParents(regionsById);
		validateNoParentCycles(regionsById);
	}

	private Set<String> validateSources(List<RegionSource> sources) {
		Set<String> sourceIds = new HashSet<>();
		for (RegionSource source : sources) {
			if (source == null) {
				throw invalid("source must not be null");
			}
			requireText(source.sourceId(), "sourceId");
			if (!sourceIds.add(source.sourceId())) {
				throw invalid("duplicate sourceId: " + source.sourceId());
			}
		}
		return sourceIds;
	}

	private void validateRegion(Region region, Set<String> sourceIds) {
		if (region == null) {
			throw invalid("region must not be null");
		}
		requireText(region.regionId(), "regionId");
		requireText(region.name(), "name");
		requireText(region.shortName(), "shortName");
		if (region.parentRegionId() != null) {
			requireText(region.parentRegionId(), "parentRegionId");
		}
		if (region.type() == null) {
			throw invalid("type must not be null");
		}
		validateTextList(region.aliases(), "aliases", true);
		validateCoordinate(region.representativeCoordinate());
		validateSearchBounds(region);
		validateAddressBoundary(region.addressBoundary());
		validateTextList(region.sourceRefs(), "sourceRefs", false);
		for (String sourceRef : region.sourceRefs()) {
			if (!sourceIds.contains(sourceRef)) {
				throw invalid("unknown sourceRef: " + sourceRef);
			}
		}
		requireText(region.sourceDate(), "sourceDate");
	}

	private void validateSearchBounds(Region region) {
		SearchBounds bounds = region.searchBounds();
		if (!region.selectable()) {
			if (bounds != null) {
				throw invalid("searchBounds is only allowed for selectable regions: " + region.regionId());
			}
			return;
		}
		if (bounds == null) {
			throw invalid("searchBounds must not be null for selectable region: " + region.regionId());
		}
		if (!Double.isFinite(bounds.minLatitude())
				|| !Double.isFinite(bounds.minLongitude())
				|| !Double.isFinite(bounds.maxLatitude())
				|| !Double.isFinite(bounds.maxLongitude())) {
			throw invalid("searchBounds must contain only finite values: " + region.regionId());
		}
		if (bounds.minLatitude() < 33 || bounds.maxLatitude() > 39
				|| bounds.minLongitude() < 124 || bounds.maxLongitude() > 132) {
			throw invalid("searchBounds is outside the supported Korea range: " + region.regionId());
		}
		if (bounds.minLatitude() >= bounds.maxLatitude()
				|| bounds.minLongitude() >= bounds.maxLongitude()) {
			throw invalid("searchBounds minimum must be less than maximum: " + region.regionId());
		}

		RepresentativeCoordinate coordinate = region.representativeCoordinate();
		if (coordinate.latitude() < bounds.minLatitude()
				|| coordinate.latitude() > bounds.maxLatitude()
				|| coordinate.longitude() < bounds.minLongitude()
				|| coordinate.longitude() > bounds.maxLongitude()) {
			throw invalid("representativeCoordinate must be inside searchBounds: " + region.regionId());
		}
	}

	private void validateCoordinate(RepresentativeCoordinate coordinate) {
		if (coordinate == null) {
			throw invalid("representativeCoordinate must not be null");
		}
		if (!Double.isFinite(coordinate.latitude())
				|| coordinate.latitude() < -90 || coordinate.latitude() > 90) {
			throw invalid("latitude is out of range");
		}
		if (!Double.isFinite(coordinate.longitude())
				|| coordinate.longitude() < -180 || coordinate.longitude() > 180) {
			throw invalid("longitude is out of range");
		}
	}

	private void validateAddressBoundary(AddressBoundary boundary) {
		if (boundary == null) {
			throw invalid("addressBoundary must not be null");
		}
		validateTextList(boundary.region1Names(), "addressBoundary.region1Names", false);
		validateTextList(boundary.region2Names(), "addressBoundary.region2Names", true);
	}

	private void validateTextList(List<String> values, String field, boolean emptyAllowed) {
		if (values == null || (!emptyAllowed && values.isEmpty())) {
			throw invalid(field + " must not be " + (emptyAllowed ? "null" : "empty"));
		}
		for (String value : values) {
			requireText(value, field);
		}
	}

	private void validateParents(Map<String, Region> regionsById) {
		for (Region region : regionsById.values()) {
			String parentId = region.parentRegionId();
			validateRole(region, parentId != null);
			if (parentId == null) {
				continue;
			}
			if (parentId.equals(region.regionId())) {
				throw invalid("region cannot be its own parent: " + region.regionId());
			}
			if (!regionsById.containsKey(parentId)) {
				throw invalid("unknown parentRegionId: " + parentId);
			}
		}
	}

	private void validateRole(Region region, boolean hasParent) {
		boolean expectedSelectable;
		boolean expectedFilterable;
		boolean expectedParent;
		switch (region.type()) {
			case SPECIAL_CITY, METROPOLITAN_CITY, SPECIAL_SELF_GOVERNING_CITY -> {
				expectedSelectable = true;
				expectedFilterable = false;
				expectedParent = false;
			}
			case PROVINCE, SPECIAL_SELF_GOVERNING_PROVINCE, INTEGRATED_SPECIAL_CITY -> {
				expectedSelectable = false;
				expectedFilterable = false;
				expectedParent = false;
			}
			case CITY, COUNTY -> {
				expectedSelectable = true;
				expectedFilterable = false;
				expectedParent = true;
			}
			case DISTRICT_FILTER, COUNTY_FILTER -> {
				expectedSelectable = false;
				expectedFilterable = true;
				expectedParent = true;
			}
			default -> throw invalid("unsupported region type: " + region.type());
		}

		if (region.selectable() != expectedSelectable
				|| region.placeSearchFilterable() != expectedFilterable
				|| hasParent != expectedParent) {
			throw invalid("region role does not match type: " + region.regionId());
		}
	}

	private void validateNoParentCycles(Map<String, Region> regionsById) {
		Set<String> fullyVisited = new HashSet<>();
		for (String regionId : regionsById.keySet()) {
			Set<String> currentPath = new HashSet<>();
			String currentId = regionId;
			while (currentId != null && !fullyVisited.contains(currentId)) {
				if (!currentPath.add(currentId)) {
					throw invalid("parent relationship contains a cycle: " + currentId);
				}
				currentId = regionsById.get(currentId).parentRegionId();
			}
			fullyVisited.addAll(currentPath);
		}
	}

	private void requireText(String value, String field) {
		if (value == null || value.isBlank() || !value.equals(value.trim())) {
			throw invalid(field + " must be non-blank text without surrounding whitespace");
		}
	}

	private IllegalStateException invalid(String reason) {
		return new IllegalStateException("Invalid region data: " + reason);
	}

	private record RegionDataFile(
			int schemaVersion,
			String generatedAt,
			List<RegionSource> sources,
			List<Region> regions
	) {
	}

	private record RegionSource(String sourceId) {
	}
}
