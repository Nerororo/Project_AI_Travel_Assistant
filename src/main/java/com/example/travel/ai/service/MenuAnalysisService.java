package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.MenuAnalysisRequest;
import com.example.travel.ai.dto.MenuAnalysisResponse;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MenuAnalysisService {

	private static final int MIN_MENU_COUNT = 1;
	private static final int MAX_MENU_COUNT = 5;

	private final AiClient aiClient;

	public MenuAnalysisService(AiClient aiClient) {
		this.aiClient = aiClient;
	}

	public MenuAnalysisResponse analyze(MenuAnalysisRequest request) {
		AiClient.MenuAnalysisResult result = aiClient.analyzeMenus(new AiClient.MenuAnalysisPrompt(
				request.regionId(),
				request.request(),
				request.attractions()
		));
		validateResult(result, request.attractions());

		List<MenuAnalysisResponse.Menu> menus = result.menus().stream()
				.map(menu -> new MenuAnalysisResponse.Menu(
						menu.name(),
						menu.searchQuery(),
						menu.reason(),
						menu.targetClientPlaceId()
				))
				.toList();
		return new MenuAnalysisResponse(menus);
	}

	private void validateResult(AiClient.MenuAnalysisResult result, List<AttractionContext> attractions) {
		if (result == null || result.menus() == null
				|| result.menus().size() < MIN_MENU_COUNT
				|| result.menus().size() > MAX_MENU_COUNT) {
			throw invalidAiResponse();
		}

		Set<String> attractionIds = new HashSet<>();
		for (AttractionContext attraction : attractions) {
			attractionIds.add(attraction.clientPlaceId());
		}

		Set<String> menuNames = new HashSet<>();
		for (AiClient.MenuSuggestion menu : result.menus()) {
			if (menu == null
					|| isBlank(menu.name())
					|| !menuNames.add(normalize(menu.name()))
					|| isBlank(menu.searchQuery())
					|| isBlank(menu.reason())
					|| menu.targetClientPlaceId() != null
						&& !attractionIds.contains(menu.targetClientPlaceId())) {
				throw invalidAiResponse();
			}
		}
	}

	private String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ApiException invalidAiResponse() {
		return new ApiException(ErrorCode.AI_RESPONSE_INVALID);
	}
}
