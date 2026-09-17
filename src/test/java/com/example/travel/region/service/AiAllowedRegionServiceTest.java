package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.domain.RegionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAllowedRegionServiceTest {

	@Test
	void exposesOnlySelectableRegionsAndAddsParentProvinceName() {
		RegionCatalog catalog = mock(RegionCatalog.class);
		Region province = region("KR-51", "강원특별자치도", null, RegionType.SPECIAL_SELF_GOVERNING_PROVINCE, false);
		Region city = region("KR-51150", "강릉시", "KR-51", RegionType.CITY, true);
		Region districtFilter = region("KR-26350", "해운대구", "KR-26", RegionType.DISTRICT_FILTER, false);
		when(catalog.regions()).thenReturn(List.of(province, city, districtFilter));
		when(catalog.findById("KR-51")).thenReturn(java.util.Optional.of(province));

		var regions = new AiAllowedRegionService(catalog).findAll();

		assertThat(regions).hasSize(1);
		assertThat(regions.getFirst().regionId()).isEqualTo("KR-51150");
		assertThat(regions.getFirst().provinceName()).isEqualTo("강원특별자치도");
	}

	@Test
	void checksOnlySelectableFinalRegionsById() {
		RegionCatalog catalog = mock(RegionCatalog.class);
		Region province = region("KR-51", "강원특별자치도", null,
				RegionType.SPECIAL_SELF_GOVERNING_PROVINCE, false);
		Region city = region("KR-51150", "강릉시", "KR-51", RegionType.CITY, true);
		Region districtFilter = region("KR-26350", "해운대구", "KR-26", RegionType.DISTRICT_FILTER, false);
		when(catalog.findById("KR-51")).thenReturn(java.util.Optional.of(province));
		when(catalog.findById("KR-51150")).thenReturn(java.util.Optional.of(city));
		when(catalog.findById("KR-26350")).thenReturn(java.util.Optional.of(districtFilter));

		AiAllowedRegionService service = new AiAllowedRegionService(catalog);

		assertThat(service.isSelectable("KR-51150")).isTrue();
		assertThat(service.isSelectable("KR-51")).isFalse();
		assertThat(service.isSelectable("KR-26350")).isFalse();
		assertThat(service.isSelectable("missing-region")).isFalse();
	}

	private static Region region(String id, String name, String parentId, RegionType type, boolean selectable) {
		return new Region(id, name, name, List.of(), parentId, type, selectable, false,
				null, null, List.of("public-source"), "2026-01-01");
	}
}
