package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.dto.RegionCandidate;
import com.example.travel.ai.dto.RegionRecommendationRequest;
import com.example.travel.ai.dto.RegionRecommendationResponse;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegionRecommendationService {

	private static final int REQUIRED_RECOMMENDATION_COUNT = 3;
	private static final int MAX_REASON_LENGTH = 200;

	private final AiClient aiClient;

	public RegionRecommendationService(AiClient aiClient) {
		this.aiClient = aiClient;
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

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ApiException invalidAiResponse() {
		return new ApiException(ErrorCode.AI_RESPONSE_INVALID);
	}
}
