package com.example.travel.region.controller;

import com.example.travel.region.dto.RegionSearchResponse;
import com.example.travel.region.service.RegionSearchService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

	private final RegionSearchService regionSearchService;

	public RegionController(RegionSearchService regionSearchService) {
		this.regionSearchService = regionSearchService;
	}

	@GetMapping
	public RegionSearchResponse search(@RequestParam @NotBlank String query) {
		return new RegionSearchResponse(regionSearchService.search(query));
	}
}
