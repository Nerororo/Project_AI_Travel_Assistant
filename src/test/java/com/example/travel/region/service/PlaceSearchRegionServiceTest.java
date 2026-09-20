package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.domain.RegionType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceSearchRegionServiceTest {

	private final RegionCatalog catalog = mock(RegionCatalog.class);
	private final PlaceSearchRegionService service = new PlaceSearchRegionService(catalog);

	@Test
	void exposesOnlyFieldsNeededForPlaceSearchValidation() {
		Region district = mock(Region.class);
		when(district.regionId()).thenReturn("KR-26350");
		when(district.parentRegionId()).thenReturn("KR-26");
		when(district.selectable()).thenReturn(false);
		when(district.placeSearchFilterable()).thenReturn(true);
		when(district.type()).thenReturn(RegionType.DISTRICT_FILTER);
		when(catalog.findById("KR-26350")).thenReturn(Optional.of(district));

		assertThat(service.findById("KR-26350"))
				.hasValueSatisfying(region -> {
					assertThat(region.regionId()).isEqualTo("KR-26350");
					assertThat(region.parentRegionId()).isEqualTo("KR-26");
					assertThat(region.selectable()).isFalse();
					assertThat(region.placeSearchFilterable()).isTrue();
				});
	}

	@Test
	void preservesAnUnknownRegionAsEmpty() {
		when(catalog.findById("UNKNOWN")).thenReturn(Optional.empty());

		assertThat(service.findById("UNKNOWN")).isEmpty();
	}
}
