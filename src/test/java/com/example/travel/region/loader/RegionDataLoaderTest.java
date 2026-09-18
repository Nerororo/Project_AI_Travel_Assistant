package com.example.travel.region.loader;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionDataLoaderTest {

	private final RegionDataLoader loader = new RegionDataLoader(new ObjectMapper());

	@Test
	void loadsRealDataAndTreatsRegionIdsAsOpaqueStrings() {
		var regions = loader.load(new ClassPathResource("data/regions.json"));

		assertThat(regions).hasSize(246);
		assertThat(regions).filteredOn(region -> region.selectable())
				.hasSize(161)
				.allSatisfy(region -> assertThat(region.searchBounds()).isNotNull());
		assertThat(regions).filteredOn(region -> !region.selectable())
				.allSatisfy(region -> assertThat(region.searchBounds()).isNull());
		assertThat(regions).anySatisfy(region -> {
			assertThat(region.regionId()).isEqualTo("KR-GWANGJU-URBAN");
			assertThat(region.name()).isEqualTo("광주");
		});
	}

	@Test
	void loadsUnionBoundsForLogicalAndAdministrativelyDividedCities() {
		var regions = loader.load(new ClassPathResource("data/regions.json"));

		var gwangju = regions.stream().filter(region -> region.regionId().equals("KR-GWANGJU-URBAN")).findFirst().orElseThrow();
		var suwon = regions.stream().filter(region -> region.regionId().equals("KR-41110")).findFirst().orElseThrow();

		assertThat(gwangju.searchBounds().minLongitude()).isLessThan(126.7);
		assertThat(gwangju.searchBounds().maxLongitude()).isGreaterThan(127.0);
		assertThat(suwon.searchBounds().minLatitude()).isLessThan(37.25);
		assertThat(suwon.searchBounds().maxLatitude()).isGreaterThan(37.33);
	}

	@Test
	void rejectsMissingInvalidAndMisplacedSearchBounds() {
		String missing = dataFile(region("A", null).replace(searchBounds(), "\"searchBounds\":null"));
		String reversed = dataFile(region("A", null).replace("\"maxLatitude\":38.0", "\"maxLatitude\":37.0"));
		String coordinateOutside = dataFile(region("A", null).replace("\"maxLongitude\":128.0", "\"maxLongitude\":126.5"));
		String boundsOnParent = dataFile(province("P").replace("\"searchBounds\":null", searchBounds()));

		assertThatThrownBy(() -> loader.load(resource(missing)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("searchBounds must not be null");
		assertThatThrownBy(() -> loader.load(resource(reversed)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("minimum must be less than maximum");
		assertThatThrownBy(() -> loader.load(resource(coordinateOutside)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("representativeCoordinate must be inside");
		assertThatThrownBy(() -> loader.load(resource(boundsOnParent)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("only allowed for selectable regions");
	}

	@Test
	void rejectsDuplicateRegionIds() {
		String json = dataFile(region("KR-11", null) + "," + region("KR-11", null));

		assertThatThrownBy(() -> loader.load(resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("duplicate regionId");
	}

	@Test
	void rejectsBlankRegionIdWithoutRequiringANumericFormat() {
		String json = dataFile(region(" ", null));

		assertThatThrownBy(() -> loader.load(resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("regionId");
	}

	@Test
	void rejectsUnknownParent() {
		String json = dataFile(region("CHILD", "MISSING"));

		assertThatThrownBy(() -> loader.load(resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("unknown parentRegionId");
	}

	@Test
	void rejectsParentCycle() {
		String json = dataFile(region("A", "B") + "," + region("B", "A"));

		assertThatThrownBy(() -> loader.load(resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cycle");
	}

	@Test
	void rejectsMissingNameAndOutOfRangeCoordinate() {
		String missingName = dataFile(region("A", null).replace("\"name\":\"Name\"", "\"name\":\"\""));
		String invalidLatitude = dataFile(region("A", null).replace("\"latitude\":37.5", "\"latitude\":91"));
		String missingCoordinate = dataFile(region("A", null)
				.replace("\"representativeCoordinate\":{\"latitude\":37.5,\"longitude\":127.0}",
						"\"representativeCoordinate\":null"));

		assertThatThrownBy(() -> loader.load(resource(missingName)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("name");
		assertThatThrownBy(() -> loader.load(resource(invalidLatitude)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("latitude");
		assertThatThrownBy(() -> loader.load(resource(missingCoordinate)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("representativeCoordinate");
	}

	@Test
	void rejectsRoleThatDoesNotMatchType() {
		String json = dataFile(region("A", null)
				.replace("\"type\":\"METROPOLITAN_CITY\"", "\"type\":\"CITY\""));

		assertThatThrownBy(() -> loader.load(resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("role does not match type");
	}

	@Test
	void rejectsUnknownSourceReference() {
		String json = dataFile(region("A", "P") + "," + province("P"))
				.replace("\"sourceRefs\":[\"SOURCE\"]", "\"sourceRefs\":[\"MISSING\"]");

		assertThatThrownBy(() -> loader.load(resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("unknown sourceRef");
	}

	@Test
	void rejectsMissingAddressBoundaryNames() {
		String missingRegion1 = dataFile(region("A", null)
				.replace("\"region1Names\":[\"Region\"]", "\"region1Names\":[]"));
		String missingRegion2List = dataFile(region("A", null)
				.replace("\"region2Names\":[]", "\"region2Names\":null"));

		assertThatThrownBy(() -> loader.load(resource(missingRegion1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("addressBoundary.region1Names");
		assertThatThrownBy(() -> loader.load(resource(missingRegion2List)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("addressBoundary.region2Names");
	}

	private ByteArrayResource resource(String json) {
		return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
	}

	private String dataFile(String regions) {
		return """
				{
				  "schemaVersion": 1,
				  "generatedAt": "2026-09-17",
				  "sources": [{"sourceId":"SOURCE"}],
				  "regions": [%s]
				}
				""".formatted(regions);
	}

	private String province(String regionId) {
		return region(regionId, null)
				.replace("\"type\":\"METROPOLITAN_CITY\"", "\"type\":\"PROVINCE\"")
				.replace("\"selectable\":true", "\"selectable\":false")
				.replace(searchBounds(), "\"searchBounds\":null");
	}

	private String searchBounds() {
		return "\"searchBounds\":{\"minLatitude\":37.0,\"minLongitude\":126.0,\"maxLatitude\":38.0,\"maxLongitude\":128.0}";
	}

	private String region(String regionId, String parentRegionId) {
		String parent = parentRegionId == null ? "null" : "\"" + parentRegionId + "\"";
		String type = parentRegionId == null ? "METROPOLITAN_CITY" : "CITY";
		return """
				{
				  "regionId":"%s",
				  "name":"Name",
				  "shortName":"Short",
				  "aliases":[],
				  "parentRegionId":%s,
				  "type":"%s",
				  "selectable":true,
				  "placeSearchFilterable":false,
				  "representativeCoordinate":{"latitude":37.5,"longitude":127.0},
				  %s,
				  "addressBoundary":{"region1Names":["Region"],"region2Names":[]},
				  "sourceRefs":["SOURCE"],
				  "sourceDate":"2026-09-17"
				}
				""".formatted(regionId, parent, type, searchBounds());
	}
}
