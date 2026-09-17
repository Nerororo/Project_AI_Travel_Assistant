package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.MenuAnalysisRequest;
import com.example.travel.ai.dto.MenuAnalysisResponse;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.region.service.AiAllowedRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MenuAnalysisService {

	private static final int MIN_MENU_COUNT = 1;
	private static final int MAX_MENU_COUNT = 5;

	private final AiClient aiClient;
	private final AiAllowedRegionService allowedRegionService;
	private final RequestExecutionService requestExecutionService;
	private final ApiUsageService apiUsageService;

	public MenuAnalysisService(
			AiClient aiClient,
			AiAllowedRegionService allowedRegionService,
			RequestExecutionService requestExecutionService,
			ApiUsageService apiUsageService
	) {
		this.aiClient = aiClient;
		this.allowedRegionService = allowedRegionService;
		this.requestExecutionService = requestExecutionService;
		this.apiUsageService = apiUsageService;
	}

	public MenuAnalysisResponse analyze(long userId, UUID requestId, MenuAnalysisRequest request) {
		validateRequestContext(request);
		RequestStartResult start = requestExecutionService.tryStart(
				userId, UsageFeature.AI_MENU_ANALYSIS, requestId, 1);
		if (!start.started()) {
			throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), start.retryAfterSeconds());
		}

		RequestExecutionLease lease = start.lease();
		try {
			MenuAnalysisResponse response = analyzeWithOneInvalidResponseRetry(userId, request);
			if (!requestExecutionService.markSucceeded(lease)) {
				throw new IllegalStateException("Request execution could not be completed");
			}
			return response;
		} catch (RuntimeException exception) {
			requestExecutionService.releaseAfterFailure(lease);
			throw exception;
		}
	}

	private void validateRequestContext(MenuAnalysisRequest request) {
		if (!allowedRegionService.isSelectable(request.regionId())) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED);
		}

		Set<String> attractionIds = new HashSet<>();
		for (AttractionContext attraction : request.attractions()) {
			if (!attractionIds.add(attraction.clientPlaceId())) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED);
			}
		}
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

	private MenuAnalysisResponse analyzeWithOneInvalidResponseRetry(
			long userId,
			MenuAnalysisRequest request
	) {
		try {
			return analyze(request);
		} catch (ApiException exception) {
			if (exception.errorCode() != ErrorCode.AI_RESPONSE_INVALID) {
				throw exception;
			}
			apiUsageService.acquireOrThrow(userId, UsageFeature.AI_MENU_ANALYSIS, 1);
			return analyze(request);
		}
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
