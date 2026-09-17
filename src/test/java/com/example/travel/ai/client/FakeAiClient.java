package com.example.travel.ai.client;

public class FakeAiClient implements AiClient {

	private RegionRecommendationResult regionResult;
	private MenuAnalysisResult menuResult;
	private RegionRecommendationPrompt lastRegionPrompt;
	private MenuAnalysisPrompt lastMenuPrompt;

	public void regionResult(RegionRecommendationResult regionResult) {
		this.regionResult = regionResult;
	}

	public void menuResult(MenuAnalysisResult menuResult) {
		this.menuResult = menuResult;
	}

	@Override
	public RegionRecommendationResult recommendRegions(RegionRecommendationPrompt prompt) {
		lastRegionPrompt = prompt;
		return regionResult;
	}

	@Override
	public MenuAnalysisResult analyzeMenus(MenuAnalysisPrompt prompt) {
		lastMenuPrompt = prompt;
		return menuResult;
	}

	public RegionRecommendationPrompt lastRegionPrompt() {
		return lastRegionPrompt;
	}

	public MenuAnalysisPrompt lastMenuPrompt() {
		return lastMenuPrompt;
	}
}
