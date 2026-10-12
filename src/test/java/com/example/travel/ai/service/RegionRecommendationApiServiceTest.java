package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.client.FakeAiClient;
import com.example.travel.ai.dto.RegionRecommendationRequest;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.region.dto.AiAllowedRegion;
import com.example.travel.region.service.AiAllowedRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionRecommendationApiServiceTest {

	private final AiAllowedRegionService allowedRegionService = mock(AiAllowedRegionService.class);
	private final RequestExecutionService requestExecutionService = mock(RequestExecutionService.class);
	private final ApiUsageService apiUsageService = mock(ApiUsageService.class);
	private FakeAiClient client;
	private RegionRecommendationService service;

	@BeforeEach
	void setUp() {
		client = new FakeAiClient();
		service = new RegionRecommendationService(client, allowedRegionService, requestExecutionService, apiUsageService);
	}

	@Test
	void connectsAuthenticatedUserRequestIdLimitAndAllowedRegions() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_REGION_RECOMMENDATION, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(allowedRegionService.findAll()).thenReturn(allowedRegions());
		when(requestExecutionService.markSucceeded(lease)).thenReturn(true);
		client.regionResult(result());

		var response = service.recommend(7L, requestId, new RegionRecommendationRequest("바다 여행"));

		assertThat(response.regions()).extracting(item -> item.regionId())
				.containsExactly("region-a", "region-b", "region-c");
		assertThat(client.lastRegionPrompt().allowedRegions()).hasSize(3);
		verify(requestExecutionService).markSucceeded(lease);
		verify(requestExecutionService, never()).releaseAfterFailure(lease);
	}

	@Test
	void rateLimitStopsBeforeRegionLookupAndAiCall() {
		UUID requestId = UUID.randomUUID();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_REGION_RECOMMENDATION, requestId, 1))
				.thenReturn(RequestStartResult.rateLimited(29));

		assertThatThrownBy(() -> service.recommend(7L, requestId, new RegionRecommendationRequest("여행")))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(exception.retryAfterSeconds()).isEqualTo(29L);
				});
		verify(allowedRegionService, never()).findAll();
		assertThat(client.lastRegionPrompt()).isNull();
	}

	@Test
	void duplicateRequestStopsBeforeUsageAndAiOrchestration() {
		UUID requestId = UUID.randomUUID();
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_REGION_RECOMMENDATION, requestId, 1))
				.thenThrow(new ApiException(ErrorCode.REQUEST_ALREADY_COMPLETED));

		assertThatThrownBy(() -> service.recommend(7L, requestId, new RegionRecommendationRequest("여행")))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode())
								.isEqualTo(ErrorCode.REQUEST_ALREADY_COMPLETED));
		verify(allowedRegionService, never()).findAll();
		assertThat(client.lastRegionPrompt()).isNull();
	}

	@Test
	void aiFailureReleasesRequestIdSoTheRequestCanBeRetried() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_REGION_RECOMMENDATION, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(allowedRegionService.findAll()).thenReturn(allowedRegions());
		client.regionFailure(new ApiException(ErrorCode.AI_UNAVAILABLE));

		assertThatThrownBy(() -> service.recommend(7L, requestId, new RegionRecommendationRequest("여행")))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
		verify(requestExecutionService).releaseAfterFailure(lease);
		verify(requestExecutionService, never()).markSucceeded(lease);
	}

	@Test
	void technicalRetryUsesTheRegionQuotaBeforeTheClientContinues() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = lease(requestId);
		AiClient retryClient = mock(AiClient.class);
		RegionRecommendationService retryService = new RegionRecommendationService(
				retryClient, allowedRegionService, requestExecutionService, apiUsageService);
		when(requestExecutionService.tryStart(7L, UsageFeature.AI_REGION_RECOMMENDATION, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(allowedRegionService.findAll()).thenReturn(allowedRegions());
		when(retryClient.recommendRegions(any(), any(Runnable.class))).thenAnswer(invocation -> {
			invocation.getArgument(1, Runnable.class).run();
			return result();
		});
		when(requestExecutionService.markSucceeded(lease)).thenReturn(true);

		assertThat(retryService.recommend(7L, requestId, new RegionRecommendationRequest("여행"))
				.regions()).hasSize(3);
		verify(apiUsageService).acquireOrThrow(7L, UsageFeature.AI_REGION_RECOMMENDATION, 1);
	}

	private static RequestExecutionLease lease(UUID requestId) {
		return new RequestExecutionLease(7L, UsageFeature.AI_REGION_RECOMMENDATION, requestId,
				Instant.parse("2026-09-17T00:10:00Z"));
	}

	private static List<AiAllowedRegion> allowedRegions() {
		return List.of(
				new AiAllowedRegion("region-a", "지역 A", "상위 A"),
				new AiAllowedRegion("region-b", "지역 B", "상위 B"),
				new AiAllowedRegion("region-c", "지역 C", "상위 C"));
	}

	private static AiClient.RegionRecommendationResult result() {
		return new AiClient.RegionRecommendationResult(List.of(
				new AiClient.RecommendedRegion("region-a", "이유 A"),
				new AiClient.RecommendedRegion("region-b", "이유 B"),
				new AiClient.RecommendedRegion("region-c", "이유 C")));
	}
}
