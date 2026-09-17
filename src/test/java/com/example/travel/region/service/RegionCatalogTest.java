package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.domain.RegionType;
import com.example.travel.region.loader.RegionDataLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCatalogTest {

	@Test
	void loadsValidatedRegionsIntoMemoryAtConstruction() {
		RegionCatalog catalog = new RegionCatalog(new RegionDataLoader(new ObjectMapper()));

		assertThat(catalog.regions()).hasSize(246);
		assertThat(catalog.findById("KR-51150"))
				.hasValueSatisfying(region -> assertThat(region.name()).isEqualTo("강릉시"));
		assertThat(catalog.findById("GANGWON_GANGNEUNG")).isEmpty();
	}

	@Test
	void exposesOnlyContractedTravelRegionsSearchFiltersAndParents() {
		RegionCatalog catalog = new RegionCatalog(new RegionDataLoader(new ObjectMapper()));

		assertThat(catalog.regions()).filteredOn(Region::selectable).hasSize(161);
		assertThat(catalog.regions()).filteredOn(Region::placeSearchFilterable).hasSize(76);
		assertThat(catalog.regions())
				.filteredOn(region -> !region.selectable() && !region.placeSearchFilterable())
				.hasSize(9)
				.allSatisfy(region -> assertThat(region.type()).isIn(
						RegionType.PROVINCE,
						RegionType.SPECIAL_SELF_GOVERNING_PROVINCE,
						RegionType.INTEGRATED_SPECIAL_CITY));

		assertThat(catalog.regions())
				.allSatisfy(region -> {
					assertThat(region.addressBoundary().region1Names()).isNotEmpty();
					assertThat(region.addressBoundary().region1Names())
							.noneMatch(name -> name.contains("일본") || name.contains("중국"));
				});
	}

	@Test
	void modelsGwangjuSejongAndDistrictCitiesUsingTheirDocumentedBoundaries() {
		RegionCatalog catalog = new RegionCatalog(new RegionDataLoader(new ObjectMapper()));

		Region gwangju = catalog.findById("KR-GWANGJU-URBAN").orElseThrow();
		assertThat(gwangju.name()).isEqualTo("광주");
		assertThat(gwangju.selectable()).isTrue();
		assertThat(catalog.regions())
				.filteredOn(region -> gwangju.regionId().equals(region.parentRegionId()))
				.extracting(Region::name)
				.containsExactlyInAnyOrder("동구", "서구", "남구", "북구", "광산구");
		assertThat(catalog.regions())
				.filteredOn(region -> gwangju.regionId().equals(region.parentRegionId()))
				.allSatisfy(region -> {
					assertThat(region.selectable()).isFalse();
					assertThat(region.placeSearchFilterable()).isTrue();
				});

		Region sejong = catalog.findById("KR-36").orElseThrow();
		assertThat(sejong.selectable()).isTrue();
		assertThat(sejong.addressBoundary().region1Names()).containsExactly("세종특별자치시");
		assertThat(sejong.addressBoundary().region2Names()).isEmpty();

		Region suwon = catalog.findById("KR-41110").orElseThrow();
		assertThat(suwon.selectable()).isTrue();
		assertThat(suwon.addressBoundary().region2Names()).containsExactlyInAnyOrderElementsOf(Set.of(
				"수원시 권선구", "수원시 영통구", "수원시 장안구", "수원시 팔달구"));
		assertThat(catalog.regions())
				.noneMatch(region -> Set.of("장안구", "권선구", "팔달구", "영통구").contains(region.name()));
	}
}
