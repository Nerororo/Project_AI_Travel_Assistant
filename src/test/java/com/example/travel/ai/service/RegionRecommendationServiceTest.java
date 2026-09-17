package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.client.FakeAiClient;
import com.example.travel.ai.dto.RegionCandidate;
import com.example.travel.ai.dto.RegionRecommendationRequest;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.region.service.AiAllowedRegionService;
import com.example.travel.user.service.RequestExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RegionRecommendationServiceTest {

	private static final List<RegionCandidate> ALLOWED_REGIONS = List.of(
			new RegionCandidate("region-a", "지역 A", "상위 A"),
			new RegionCandidate("region-b", "지역 B", "상위 B"),
			new RegionCandidate("region-c", "지역 C", "상위 C"),
			new RegionCandidate("region-d", "지역 D", "상위 D")
	);

	private FakeAiClient client;
	private RegionRecommendationService service;

	@BeforeEach
	void setUp() {
		client = new FakeAiClient();
		service = new RegionRecommendationService(
				client,
				mock(AiAllowedRegionService.class),
				mock(RequestExecutionService.class));
	}

	@Test
	void returnsExactlyThreeAllowedRegionsEnrichedWithServerData() {
		client.regionResult(regionResult("region-c", "region-a", "region-b"));

		var response = service.recommend(
				new RegionRecommendationRequest("  조용한 여행  "),
				ALLOWED_REGIONS
		);

		assertThat(client.lastRegionPrompt().request()).isEqualTo("조용한 여행");
		assertThat(client.lastRegionPrompt().allowedRegions()).isEqualTo(ALLOWED_REGIONS);
		assertThat(response.regions())
				.extracting(item -> item.regionId())
				.containsExactly("region-c", "region-a", "region-b");
		assertThat(response.regions().get(0).name()).isEqualTo("지역 C");
		assertThat(response.regions().get(0).provinceName()).isEqualTo("상위 C");
	}

	@Test
	void rejectsUnknownDuplicateAndWrongRegionCounts() {
		assertInvalid(regionResult("region-a", "region-b", "unknown"));
		assertInvalid(regionResult("region-a", "region-a", "region-b"));
		assertInvalid(regionResult("region-a", "region-b"));
		assertInvalid(new AiClient.RegionRecommendationResult(null));
	}

	@Test
	void rejectsMissingOrTooLongReasons() {
		client.regionResult(new AiClient.RegionRecommendationResult(List.of(
				new AiClient.RecommendedRegion("region-a", " "),
				new AiClient.RecommendedRegion("region-b", "이유 B"),
				new AiClient.RecommendedRegion("region-c", "이유 C")
		)));
		assertInvalidCurrentResult();

		client.regionResult(new AiClient.RegionRecommendationResult(List.of(
				new AiClient.RecommendedRegion("region-a", "가".repeat(201)),
				new AiClient.RecommendedRegion("region-b", "이유 B"),
				new AiClient.RecommendedRegion("region-c", "이유 C")
		)));
		assertInvalidCurrentResult();
	}

	private AiClient.RegionRecommendationResult regionResult(String... regionIds) {
		return new AiClient.RegionRecommendationResult(
				java.util.Arrays.stream(regionIds)
						.map(id -> new AiClient.RecommendedRegion(id, "안전한 추천 이유"))
						.toList()
		);
	}

	private void assertInvalid(AiClient.RegionRecommendationResult result) {
		client.regionResult(result);
		assertInvalidCurrentResult();
	}

	private void assertInvalidCurrentResult() {
		assertThatThrownBy(() -> service.recommend(
				new RegionRecommendationRequest("여행 요청"),
				ALLOWED_REGIONS
		))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
	}
}
