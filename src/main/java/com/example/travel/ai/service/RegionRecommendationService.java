package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.dto.RegionCandidate;
import com.example.travel.ai.dto.RegionRecommendationRequest;
import com.example.travel.ai.dto.RegionRecommendationResponse;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.region.dto.AiAllowedRegion;
import com.example.travel.region.service.AiAllowedRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.RequestExecutionService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RegionRecommendationService {

	private static final int REQUIRED_RECOMMENDATION_COUNT = 3;
	private static final int MAX_REASON_LENGTH = 200;

	private final AiClient aiClient;
	private final AiAllowedRegionService allowedRegionService;
	private final RequestExecutionService requestExecutionService;

	public RegionRecommendationService(
			AiClient aiClient,
			AiAllowedRegionService allowedRegionService,
			RequestExecutionService requestExecutionService
	) {
		this.aiClient = aiClient;
		this.allowedRegionService = allowedRegionService;
		this.requestExecutionService = requestExecutionService;
	}

	public RegionRecommendationResponse recommend(
			long userId,
			UUID requestId,
			RegionRecommendationRequest request
	) {
		RequestStartResult start = requestExecutionService.tryStart(
				userId, UsageFeature.AI_REGION_RECOMMENDATION, requestId, 1);
		if (!start.started()) {
			throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), start.retryAfterSeconds());
		}

		RequestExecutionLease lease = start.lease();
		try {
			List<RegionCandidate> allowedRegions = allowedRegionService.findAll().stream()
					.map(this::toCandidate)
					.toList();
			RegionRecommendationResponse response = recommend(request, allowedRegions);
			if (!requestExecutionService.markSucceeded(lease)) {
				throw new IllegalStateException("Request execution could not be completed");
			}
			return response;
		} catch (RuntimeException exception) {
			requestExecutionService.releaseAfterFailure(lease);
			throw exception;
		}
	}

	public RegionRecommendationResponse recommend(
			RegionRecommendationRequest request,
			List<RegionCandidate> allowedRegions
	) {
		Map<String, RegionCandidate> candidatesById = indexCandidates(allowedRegions);
		if (candidatesById.size() < REQUIRED_RECOMMENDATION_COUNT) {
			throw new IllegalArgumentException("At least three allowed regions are required");
		}

		AiClient.RegionRecommendationResult result = aiClient.recommendRegions(
				new AiClient.RegionRecommendationPrompt(request.request(), allowedRegions)
		);
		validateResult(result, candidatesById);

		List<RegionRecommendationResponse.RecommendedRegion> recommendations = result.regions().stream()
				.map(recommendation -> toResponse(recommendation, candidatesById.get(recommendation.regionId())))
				.toList();
		return new RegionRecommendationResponse(recommendations);
	}

	private Map<String, RegionCandidate> indexCandidates(List<RegionCandidate> allowedRegions) {
		if (allowedRegions == null) {
			throw new IllegalArgumentException("allowedRegions must not be null");
		}

		Map<String, RegionCandidate> candidatesById = new LinkedHashMap<>();
		for (RegionCandidate candidate : allowedRegions) {
			if (candidate == null || isBlank(candidate.regionId()) || isBlank(candidate.name())) {
				throw new IllegalArgumentException("Allowed region fields must not be blank");
			}
			if (candidatesById.putIfAbsent(candidate.regionId(), candidate) != null) {
				throw new IllegalArgumentException("Allowed region IDs must be unique");
			}
		}
		return candidatesById;
	}

	private void validateResult(
			AiClient.RegionRecommendationResult result,
			Map<String, RegionCandidate> candidatesById
	) {
		if (result == null || result.regions() == null
				|| result.regions().size() != REQUIRED_RECOMMENDATION_COUNT) {
			throw invalidAiResponse();
		}

		Set<String> selectedIds = new HashSet<>();
		for (AiClient.RecommendedRegion recommendation : result.regions()) {
			if (recommendation == null
					|| isBlank(recommendation.regionId())
					|| !candidatesById.containsKey(recommendation.regionId())
					|| !selectedIds.add(recommendation.regionId())
					|| isBlank(recommendation.reason())
					|| recommendation.reason().length() > MAX_REASON_LENGTH) {
				throw invalidAiResponse();
			}
		}
	}

	private RegionRecommendationResponse.RecommendedRegion toResponse(
			AiClient.RecommendedRegion recommendation,
			RegionCandidate candidate
	) {
		return new RegionRecommendationResponse.RecommendedRegion(
				candidate.regionId(),
				candidate.name(),
				candidate.provinceName(),
				recommendation.reason()
		);
	}

	private RegionCandidate toCandidate(AiAllowedRegion region) {
		return new RegionCandidate(region.regionId(), region.name(), region.provinceName());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ApiException invalidAiResponse() {
		return new ApiException(ErrorCode.AI_RESPONSE_INVALID);
	}
}
