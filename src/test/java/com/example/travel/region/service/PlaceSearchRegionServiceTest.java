package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.domain.RegionType;
import com.example.travel.region.domain.AddressBoundary;
import com.example.travel.region.domain.RepresentativeCoordinate;
import com.example.travel.region.domain.SearchBounds;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

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
		when(district.name()).thenReturn("해운대구");
		when(district.representativeCoordinate()).thenReturn(new RepresentativeCoordinate(36.0, 127.0));
		when(district.searchBounds()).thenReturn(new SearchBounds(35.0, 126.0, 37.0, 128.0));
		when(district.addressBoundary()).thenReturn(
				new AddressBoundary(List.of("테스트광역시"), List.of("해운대구")));
		when(district.type()).thenReturn(RegionType.DISTRICT_FILTER);
		when(catalog.findById("KR-26350")).thenReturn(Optional.of(district));

		assertThat(service.findById("KR-26350"))
				.hasValueSatisfying(region -> {
					assertThat(region.regionId()).isEqualTo("KR-26350");
					assertThat(region.parentRegionId()).isEqualTo("KR-26");
					assertThat(region.selectable()).isFalse();
					assertThat(region.placeSearchFilterable()).isTrue();
					assertThat(region.name()).isEqualTo("해운대구");
					assertThat(region.representativeCoordinate())
							.isEqualTo(new com.example.travel.region.dto.PlaceSearchRegion.Coordinate(36.0, 127.0));
					assertThat(region.addressBoundary().region2Names()).containsExactly("해운대구");
				});
	}

	@Test
	void preservesAnUnknownRegionAsEmpty() {
		when(catalog.findById("UNKNOWN")).thenReturn(Optional.empty());

		assertThat(service.findById("UNKNOWN")).isEmpty();
	}
}
