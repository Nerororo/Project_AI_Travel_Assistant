package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.RestaurantSearchApiRequest;
import com.example.travel.travelplan.dto.RestaurantSearchApiResponse;
import com.example.travel.travelplan.service.RestaurantSearchCoordinationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places/restaurants")
public class RestaurantSearchController {
	private final RestaurantSearchCoordinationService coordinationService;

	public RestaurantSearchController(RestaurantSearchCoordinationService coordinationService) {
		this.coordinationService = coordinationService;
	}

	@PostMapping("/search")
	public RestaurantSearchApiResponse search(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader("Idempotency-Key") UUID requestId,
			@Valid @RequestBody RestaurantSearchApiRequest request) {
		return coordinationService.search(user.userId(), requestId, request);
	}
}
