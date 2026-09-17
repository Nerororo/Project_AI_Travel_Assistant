package com.example.travel.ai.dto;

import java.util.List;

public record MenuAnalysisResponse(List<Menu> menus) {

	public MenuAnalysisResponse {
		menus = List.copyOf(menus);
	}

	public record Menu(
			String name,
			String searchQuery,
			String reason,
			String targetClientPlaceId
	) {
	}
}
