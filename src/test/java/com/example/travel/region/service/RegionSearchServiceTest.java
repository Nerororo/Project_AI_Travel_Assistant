package com.example.travel.region.service;

import com.example.travel.region.loader.RegionDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionSearchServiceTest {

	private RegionSearchService service;

	@BeforeEach
	void setUp() {
		RegionCatalog catalog = new RegionCatalog(new RegionDataLoader(new ObjectMapper()));
		service = new RegionSearchService(catalog);
	}

	@Test
	void searchesOfficialShortAndAliasNames() {
		assertThat(service.search("강릉"))
				.extracting(item -> item.regionId())
				.containsExactly("KR-51150");
		assertThat(service.search("전라남도"))
				.first()
				.satisfies(item -> {
					assertThat(item.regionId()).isEqualTo("KR-12");
					assertThat(item.name()).isEqualTo("전남광주통합특별시");
				});
	}

	@Test
	void returnsDuplicateNamesWithParentNamesAndStableOrder() {
		var first = service.search("동구");
		var second = service.search("동구");

		assertThat(first).isEqualTo(second);
		assertThat(first).hasSizeGreaterThan(1);
		assertThat(first).allSatisfy(item -> assertThat(item.provinceName()).isNotBlank());
		assertThat(first).extracting(item -> item.regionId()).doesNotHaveDuplicates();
	}

	@Test
	void ranksExactBeforePrefixAndContainsMatches() {
		var results = service.search("광주");

		assertThat(results).isNotEmpty();
		assertThat(results.get(0).regionId()).isEqualTo("KR-GWANGJU-URBAN");
		assertThat(results).extracting(item -> item.regionId()).contains("KR-41610");
	}

	@Test
	void distinguishesSelectableRegionsParentsAndSearchFilters() {
		var seoul = service.search("서울특별시").get(0);
		var gangwon = service.search("강원특별자치도").get(0);
		var haeundae = service.search("해운대").get(0);

		assertThat(seoul.selectable()).isTrue();
		assertThat(seoul.placeSearchFilterable()).isFalse();
		assertThat(gangwon.selectable()).isFalse();
		assertThat(gangwon.placeSearchFilterable()).isFalse();
		assertThat(haeundae.selectable()).isFalse();
		assertThat(haeundae.placeSearchFilterable()).isTrue();
		assertThat(haeundae.provinceName()).isEqualTo("부산광역시");
	}

	@Test
	void trimsQueryAndRejectsBlankInput() {
		assertThat(service.search("  해운대  "))
				.extracting(item -> item.regionId())
				.containsExactly("KR-26350");
		assertThatThrownBy(() -> service.search("   "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
