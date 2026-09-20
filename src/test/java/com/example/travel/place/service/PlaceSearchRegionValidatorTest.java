package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.PlaceSearchRegionCriteria;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaceSearchRegionValidatorTest {

	private final PlaceSearchRegionService regionService = mock(PlaceSearchRegionService.class);
	private final PlaceSearchRegionValidator validator = new PlaceSearchRegionValidator(regionService);

	@BeforeEach
	void setUpRegions() {
		when(regionService.findById("KR-26"))
				.thenReturn(Optional.of(region("KR-26", null, true, false)));
		when(regionService.findById("KR-26350"))
				.thenReturn(Optional.of(region("KR-26350", "KR-26", false, true)));
	}

	@Test
	void acceptsAttractionSearchWithoutAnOptionalDistrictFilter() {
		validator.validate(criteria("KR-26", null, PlaceRole.ATTRACTION));
	}

	@Test
	void acceptsAttractionFilterBelongingToTheFinalTravelRegion() {
		validator.validate(criteria("KR-26", "KR-26350", PlaceRole.ATTRACTION));
	}

	@ParameterizedTest
	@EnumSource(value = PlaceRole.class, names = {"HOTEL", "RESTAURANT"})
	void rejectsDistrictFilterForNonAttractionSearch(PlaceRole placeRole) {
		assertValidationFailure(criteria("KR-26", "KR-26350", placeRole));
	}

	@Test
	void rejectsFilterFromAnotherParentRegion() {
		when(regionService.findById("KR-11680"))
				.thenReturn(Optional.of(region("KR-11680", "KR-11", false, true)));

		assertValidationFailure(criteria("KR-26", "KR-11680", PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsFinalTravelRegionUsedAsDistrictFilter() {
		assertValidationFailure(criteria("KR-26", "KR-26", PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsNonFilterableAndUnknownDistrictIds() {
		when(regionService.findById("KR-36"))
				.thenReturn(Optional.of(region("KR-36", null, true, false)));
		when(regionService.findById("UNKNOWN")).thenReturn(Optional.empty());

		assertValidationFailure(criteria("KR-26", "KR-36", PlaceRole.ATTRACTION));
		assertValidationFailure(criteria("KR-26", "UNKNOWN", PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsNonSelectableOrUnknownFinalRegion() {
		when(regionService.findById("KR-51"))
				.thenReturn(Optional.of(region("KR-51", null, false, false)));
		when(regionService.findById("UNKNOWN")).thenReturn(Optional.empty());

		assertValidationFailure(criteria("KR-51", null, PlaceRole.ATTRACTION));
		assertValidationFailure(criteria("KR-26350", null, PlaceRole.ATTRACTION));
		assertValidationFailure(criteria("UNKNOWN", null, PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsMissingOrBlankContractFieldsBeforeRegionLookup() {
		PlaceSearchRegionService untouchedService = mock(PlaceSearchRegionService.class);
		PlaceSearchRegionValidator untouchedValidator = new PlaceSearchRegionValidator(untouchedService);

		assertValidationFailure(untouchedValidator, null);
		assertValidationFailure(untouchedValidator, criteria(null, null, PlaceRole.ATTRACTION));
		assertValidationFailure(untouchedValidator, criteria(" ", null, PlaceRole.ATTRACTION));
		assertValidationFailure(untouchedValidator, criteria("KR-26", null, null));
		verifyNoInteractions(untouchedService);
	}

	@Test
	void rejectsBlankDistrictFilterInsteadOfTreatingItAsAbsent() {
		assertValidationFailure(criteria("KR-26", " ", PlaceRole.ATTRACTION));
	}

	private void assertValidationFailure(PlaceSearchRegionCriteria criteria) {
		assertValidationFailure(validator, criteria);
	}

	private void assertValidationFailure(
			PlaceSearchRegionValidator target,
			PlaceSearchRegionCriteria criteria
	) {
		assertThatThrownBy(() -> target.validate(criteria))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	private PlaceSearchRegionCriteria criteria(String regionId, String districtFilterId, PlaceRole placeRole) {
		return new PlaceSearchRegionCriteria(regionId, districtFilterId, placeRole);
	}

	private PlaceSearchRegion region(
			String regionId,
			String parentRegionId,
			boolean selectable,
			boolean placeSearchFilterable
	) {
		return new PlaceSearchRegion(regionId, parentRegionId, selectable, placeSearchFilterable);
	}
}
