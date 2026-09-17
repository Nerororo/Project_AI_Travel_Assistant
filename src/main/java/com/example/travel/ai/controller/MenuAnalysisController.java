package com.example.travel.ai.controller;

import com.example.travel.ai.dto.MenuAnalysisRequest;
import com.example.travel.ai.dto.MenuAnalysisResponse;
import com.example.travel.ai.service.MenuAnalysisService;
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
@RequestMapping("/api/ai/menus")
public class MenuAnalysisController {

	private final MenuAnalysisService menuAnalysisService;

	public MenuAnalysisController(MenuAnalysisService menuAnalysisService) {
		this.menuAnalysisService = menuAnalysisService;
	}

	@PostMapping("/analyze")
	public MenuAnalysisResponse analyze(
			@AuthenticationPrincipal AuthenticatedUser user,
			@RequestHeader("Idempotency-Key") UUID requestId,
			@Valid @RequestBody MenuAnalysisRequest request
	) {
		return menuAnalysisService.analyze(user.userId(), requestId, request);
	}
}
