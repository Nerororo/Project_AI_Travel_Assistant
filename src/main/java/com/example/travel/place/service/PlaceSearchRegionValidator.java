package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.PlaceSearchRegionCriteria;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Validates the final travel region and optional district filter before place-provider calls.
 */
@Service
public class PlaceSearchRegionValidator {

	private final PlaceSearchRegionService regionService;

	public PlaceSearchRegionValidator(PlaceSearchRegionService regionService) {
		this.regionService = Objects.requireNonNull(regionService, "regionService must not be null");
	}

	public void validate(PlaceSearchRegionCriteria criteria) {
		if (criteria == null || isBlank(criteria.regionId()) || criteria.placeRole() == null) {
			throw validationFailed();
		}

		PlaceSearchRegion travelRegion = regionService.findById(criteria.regionId())
				.filter(PlaceSearchRegion::selectable)
				.orElseThrow(this::validationFailed);

		if (criteria.districtFilterId() == null) {
			return;
		}
		if (criteria.districtFilterId().isBlank() || criteria.placeRole() != PlaceRole.ATTRACTION) {
			throw validationFailed();
		}

		PlaceSearchRegion districtFilter = regionService.findById(criteria.districtFilterId())
				.filter(PlaceSearchRegion::placeSearchFilterable)
				.filter(region -> !region.selectable())
				.orElseThrow(this::validationFailed);
		if (!travelRegion.regionId().equals(districtFilter.parentRegionId())) {
			throw validationFailed();
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}
}
