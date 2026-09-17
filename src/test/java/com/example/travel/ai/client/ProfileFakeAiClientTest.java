package com.example.travel.ai.client;

import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.RegionCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileFakeAiClientTest {

	private final ProfileFakeAiClient client = new ProfileFakeAiClient();

	@Test
	void returnsThreeCandidatesFromTheAllowedListOnly() {
		var allowedRegions = List.of(
				new RegionCandidate("region-a", "지역 A", null),
				new RegionCandidate("region-b", "지역 B", null),
				new RegionCandidate("region-c", "지역 C", null),
				new RegionCandidate("region-d", "지역 D", null)
		);

		var result = client.recommendRegions(
				new AiClient.RegionRecommendationPrompt("테스트 요청", allowedRegions)
		);

		assertThat(result.regions())
				.extracting(AiClient.RecommendedRegion::regionId)
				.containsExactly("region-a", "region-b", "region-c");
	}

	@Test
	void returnsAContractCompliantMenuWithoutSelectingAnAttraction() {
		var result = client.analyzeMenus(new AiClient.MenuAnalysisPrompt(
				"region-a",
				"테스트 요청",
				List.of(new AttractionContext("place-a", "사용자 장소 A"))
		));

		assertThat(result.menus()).hasSize(1);
		assertThat(result.menus().get(0).targetClientPlaceId()).isNull();
	}
}
