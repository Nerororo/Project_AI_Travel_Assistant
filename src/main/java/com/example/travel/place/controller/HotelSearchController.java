package com.example.travel.place.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.HotelSearchApiRequest;
import com.example.travel.place.dto.HotelSearchApiResponse;
import com.example.travel.place.service.HotelSearchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/places/hotels")
public class HotelSearchController {
	private final HotelSearchService hotelSearchService;

	public HotelSearchController(HotelSearchService hotelSearchService) {
		this.hotelSearchService = hotelSearchService;
	}

	@PostMapping("/search")
	public HotelSearchApiResponse search(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader("Idempotency-Key") UUID requestId,
			@Valid @RequestBody HotelSearchApiRequest request
	) {
		return hotelSearchService.search(user.userId(), requestId, request);
	}
}
