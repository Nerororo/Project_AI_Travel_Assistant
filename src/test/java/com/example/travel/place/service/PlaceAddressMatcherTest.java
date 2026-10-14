package com.example.travel.place.service;

import com.example.travel.region.dto.PlaceSearchRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceAddressMatcherTest {

	@Test
	void acceptsCityPartOfMultiwordAdministrativeNames() {
		PlaceSearchRegion jeonju = region(List.of("전북특별자치도"),
				List.of("전주시 완산구", "전주시 덕진구"));

		assertThat(PlaceAddressMatcher.matches("전북특별자치도 전주시 완산구 예시로 1", jeonju)).isTrue();
		assertThat(PlaceAddressMatcher.matches("전북특별자치도 전주시 덕진구 예시로 2", jeonju)).isTrue();
		assertThat(PlaceAddressMatcher.matches("전북특별자치도 완주군 전주시로 1", jeonju)).isFalse();
		assertThat(PlaceAddressMatcher.matches("전남특별자치도 전주시 완산구 예시로 1", jeonju)).isFalse();
		assertThat(PlaceAddressMatcher.matches("전북특별자치도 전주신시 완산구 예시로 1", jeonju)).isFalse();
	}

	@Test
	void keepsSingleWordDistrictFilterExact() {
		PlaceSearchRegion gangnam = region(List.of("서울"), List.of("강남구"));

		assertThat(PlaceAddressMatcher.matches("서울 강남구 예시로 1", gangnam)).isTrue();
		assertThat(PlaceAddressMatcher.matches("서울 강북구 강남대로 1", gangnam)).isFalse();
		assertThat(PlaceAddressMatcher.matches("경기 강남구 예시로 1", gangnam)).isFalse();
	}

	private PlaceSearchRegion region(List<String> region1Names, List<String> region2Names) {
		return new PlaceSearchRegion("KR-TEST", "테스트", null, true, false, null, null,
				new PlaceSearchRegion.AddressBoundary(region1Names, region2Names));
	}
}
