package com.example.travel.place.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.PlaceSearchApiRequest;
import com.example.travel.place.dto.PlaceSearchApiResponse;
import com.example.travel.place.service.PlaceSearchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/places")
public class PlaceSearchController {

	private final PlaceSearchService placeSearchService;

	public PlaceSearchController(PlaceSearchService placeSearchService) {
		this.placeSearchService = placeSearchService;
	}

	@PostMapping("/search")
	public PlaceSearchApiResponse search(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader("Idempotency-Key") UUID requestId,
			@Valid @RequestBody PlaceSearchApiRequest request
	) {
		return placeSearchService.search(user.userId(), requestId, request);
	}
}
