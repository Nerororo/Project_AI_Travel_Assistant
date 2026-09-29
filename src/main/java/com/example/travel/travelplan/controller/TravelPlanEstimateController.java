package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiResponse;
import com.example.travel.travelplan.service.TravelPlanEstimateApiService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanEstimateController {

	private final TravelPlanEstimateApiService estimateApiService;

	public TravelPlanEstimateController(TravelPlanEstimateApiService estimateApiService) {
		this.estimateApiService = estimateApiService;
	}

	@PostMapping("/estimate")
	public TravelPlanEstimateApiResponse estimate(
			@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody TravelPlanEstimateApiRequest request
	) {
		return estimateApiService.estimate(user.userId(), request);
	}
}
