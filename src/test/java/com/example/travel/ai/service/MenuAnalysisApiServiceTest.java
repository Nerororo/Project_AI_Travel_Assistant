package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.MenuAnalysisRequest;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.region.service.AiAllowedRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MenuAnalysisApiServiceTest {

	private final AiClient client = mock(AiClient.class);
	private final AiAllowedRegionService allowedRegionService = mock(AiAllowedRegionService.class);
	private final RequestExecutionService requestExecutionService = mock(RequestExecutionService.class);
	private final ApiUsageService apiUsageService = mock(ApiUsageService.class);
	private final MenuAnalysisService service =
			new MenuAnalysisService(client, allowedRegionService, requestExecutionService, apiUsageService);

	@BeforeEach
	void allowRequestRegion() {
		when(allowedRegionService.isSelectable("KR-26")).thenReturn(true);
	}

	@Test
	void connectsUserRequestIdAndMenuUsageBeforeCallingAi() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		MenuAnalysisRequest request = request();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_MENU_ANALYSIS, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(client.analyzeMenus(prompt(request))).thenReturn(validResult());
		when(requestExecutionService.markSucceeded(lease)).thenReturn(true);

		var response = service.analyze(7L, requestId, request);

		assertThat(response.menus()).hasSize(1);
		verify(client).analyzeMenus(prompt(request));
		verify(requestExecutionService).markSucceeded(lease);
		verifyNoInteractions(apiUsageService);
	}

	@Test
	void retriesInvalidStructureOnceAndChargesTheSecondAiCall() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		MenuAnalysisRequest request = request();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_MENU_ANALYSIS, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(client.analyzeMenus(prompt(request)))
				.thenReturn(new AiClient.MenuAnalysisResult(List.of()))
				.thenReturn(validResult());
		when(requestExecutionService.markSucceeded(lease)).thenReturn(true);

		var response = service.analyze(7L, requestId, request);

		assertThat(response.menus()).hasSize(1);
		verify(apiUsageService).acquireOrThrow(7L, UsageFeature.AI_MENU_ANALYSIS, 1);
		verify(client, times(2)).analyzeMenus(prompt(request));
		verify(requestExecutionService).markSucceeded(lease);
	}

	@Test
	void secondInvalidStructureFailsAndReleasesRequestIdForDirectInputFallback() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		MenuAnalysisRequest request = request();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_MENU_ANALYSIS, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(client.analyzeMenus(prompt(request)))
				.thenReturn(new AiClient.MenuAnalysisResult(List.of()));

		assertThatThrownBy(() -> service.analyze(7L, requestId, request))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
		verify(client, times(2)).analyzeMenus(prompt(request));
		verify(apiUsageService).acquireOrThrow(7L, UsageFeature.AI_MENU_ANALYSIS, 1);
		verify(requestExecutionService).releaseAfterFailure(lease);
		verify(requestExecutionService, never()).markSucceeded(lease);
	}

	@Test
	void retryRateLimitStopsBeforeSecondAiCall() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		MenuAnalysisRequest request = request();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_MENU_ANALYSIS, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(client.analyzeMenus(prompt(request))).thenReturn(new AiClient.MenuAnalysisResult(List.of()));
		doThrow(new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), 22L))
				.when(apiUsageService).acquireOrThrow(7L, UsageFeature.AI_MENU_ANALYSIS, 1);

		assertThatThrownBy(() -> service.analyze(7L, requestId, request))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(exception.retryAfterSeconds()).isEqualTo(22L);
				});
		verify(client).analyzeMenus(prompt(request));
		verify(requestExecutionService).releaseAfterFailure(lease);
	}

	@Test
	void duplicateStopsBeforeAiAndAdditionalUsage() {
		UUID requestId = UUID.randomUUID();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_MENU_ANALYSIS, requestId, 1))
				.thenThrow(new ApiException(ErrorCode.REQUEST_IN_PROGRESS));

		assertThatThrownBy(() -> service.analyze(7L, requestId, request()))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
		verifyNoInteractions(client, apiUsageService);
	}

	@Test
	void initialRateLimitStopsBeforeAiCall() {
		UUID requestId = UUID.randomUUID();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_MENU_ANALYSIS, requestId, 1))
				.thenReturn(RequestStartResult.rateLimited(33));

		assertThatThrownBy(() -> service.analyze(7L, requestId, request()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(exception.retryAfterSeconds()).isEqualTo(33L);
				});
		verifyNoInteractions(client, apiUsageService);
	}

	@Test
	void invalidOrDuplicateContextStopsBeforeRequestIdUsageAndAi() {
		UUID requestId = UUID.randomUUID();
		MenuAnalysisRequest invalidRegion = new MenuAnalysisRequest(
				"KR-51", "지역 음식", List.of());

		assertThatThrownBy(() -> service.analyze(7L, requestId, invalidRegion))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

		MenuAnalysisRequest duplicateAttractions = new MenuAnalysisRequest(
				"KR-26",
				"지역 음식",
				List.of(
						new AttractionContext("same-place", "장소 A"),
						new AttractionContext("same-place", "장소 B")));

		assertThatThrownBy(() -> service.analyze(7L, requestId, duplicateAttractions))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verifyNoInteractions(requestExecutionService, client, apiUsageService);
	}

	private static MenuAnalysisRequest request() {
		return new MenuAnalysisRequest(
				"KR-26",
				"부산다운 음식",
				List.of(new AttractionContext("browser-place", "사용자 장소")));
	}

	private static AiClient.MenuAnalysisPrompt prompt(MenuAnalysisRequest request) {
		return new AiClient.MenuAnalysisPrompt(request.regionId(), request.request(), request.attractions());
	}

	private static AiClient.MenuAnalysisResult validResult() {
		return new AiClient.MenuAnalysisResult(List.of(
				new AiClient.MenuSuggestion("돼지국밥", "돼지국밥", "지역 음식", "browser-place")));
	}

	private static RequestExecutionLease lease(UUID requestId) {
		return new RequestExecutionLease(7L, UsageFeature.AI_MENU_ANALYSIS, requestId,
				Instant.parse("2026-09-17T00:10:00Z"));
	}
}
