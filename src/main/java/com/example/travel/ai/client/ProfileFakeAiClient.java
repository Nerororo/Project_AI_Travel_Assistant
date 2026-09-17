package com.example.travel.ai.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile({"local", "test"})
public class ProfileFakeAiClient implements AiClient {

	private static final String REGION_REASON = "테스트용 지역 추천입니다.";
	private static final String MENU_NAME = "테스트 메뉴";
	private static final String MENU_SEARCH_QUERY = "테스트 메뉴";
	private static final String MENU_REASON = "테스트용 메뉴 분석입니다.";

	@Override
	public RegionRecommendationResult recommendRegions(RegionRecommendationPrompt prompt) {
		List<RecommendedRegion> regions = prompt.allowedRegions().stream()
				.limit(3)
				.map(region -> new RecommendedRegion(region.regionId(), REGION_REASON))
				.toList();
		return new RegionRecommendationResult(regions);
	}

	@Override
	public MenuAnalysisResult analyzeMenus(MenuAnalysisPrompt prompt) {
		return new MenuAnalysisResult(List.of(
				new MenuSuggestion(MENU_NAME, MENU_SEARCH_QUERY, MENU_REASON, null)
		));
	}
}
