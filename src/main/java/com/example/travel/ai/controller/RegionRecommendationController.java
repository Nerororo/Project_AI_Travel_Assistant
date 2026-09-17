package com.example.travel.ai.controller;

import com.example.travel.ai.dto.RegionRecommendationRequest;
import com.example.travel.ai.dto.RegionRecommendationResponse;
import com.example.travel.ai.service.RegionRecommendationService;
import com.example.travel.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/regions")
public class RegionRecommendationController {

	private final RegionRecommendationService recommendationService;

	public RegionRecommendationController(RegionRecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@PostMapping("/recommend")
	public RegionRecommendationResponse recommend(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader("Idempotency-Key") UUID requestId,
			@Valid @RequestBody RegionRecommendationRequest request
	) {
		return recommendationService.recommend(user.userId(), requestId, request);
	}
}
