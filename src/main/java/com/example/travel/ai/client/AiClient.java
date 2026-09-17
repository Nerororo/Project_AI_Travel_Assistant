package com.example.travel.ai.client;

import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.RegionCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface AiClient {

	RegionRecommendationResult recommendRegions(RegionRecommendationPrompt prompt);

	MenuAnalysisResult analyzeMenus(MenuAnalysisPrompt prompt);

	record RegionRecommendationPrompt(String request, List<RegionCandidate> allowedRegions) {

		public RegionRecommendationPrompt {
			allowedRegions = immutableCopyAllowingNullElements(allowedRegions);
		}
	}

	record RegionRecommendationResult(List<RecommendedRegion> regions) {

		public RegionRecommendationResult {
			regions = immutableCopyAllowingNullElements(regions);
		}
	}

	record RecommendedRegion(String regionId, String reason) {
	}

	record MenuAnalysisPrompt(
			String regionId,
			String request,
			List<AttractionContext> attractions
	) {

		public MenuAnalysisPrompt {
			attractions = immutableCopyAllowingNullElements(attractions);
		}
	}

	record MenuAnalysisResult(List<MenuSuggestion> menus) {

		public MenuAnalysisResult {
			menus = immutableCopyAllowingNullElements(menus);
		}
	}

	record MenuSuggestion(
			String name,
			String searchQuery,
			String reason,
			String targetClientPlaceId
	) {
	}

	private static <T> List<T> immutableCopyAllowingNullElements(List<T> values) {
		if (values == null) {
			return null;
		}
		return Collections.unmodifiableList(new ArrayList<>(values));
	}
}
