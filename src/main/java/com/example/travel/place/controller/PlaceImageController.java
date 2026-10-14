package com.example.travel.place.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.PlaceImageApiRequest;
import com.example.travel.place.dto.PlaceImageApiResponse;
import com.example.travel.place.service.PlaceImageService;
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
public class PlaceImageController {
	private final PlaceImageService service;

	public PlaceImageController(PlaceImageService service) {
		this.service = service;
	}

	@PostMapping("/image")
	public PlaceImageApiResponse image(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader("Idempotency-Key") UUID requestId,
			@Valid @RequestBody PlaceImageApiRequest request
	) {
		return service.search(user.userId(), requestId, request);
	}
}
