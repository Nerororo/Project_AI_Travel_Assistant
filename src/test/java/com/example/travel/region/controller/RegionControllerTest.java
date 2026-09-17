package com.example.travel.region.controller;

import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.region.domain.RegionType;
import com.example.travel.region.dto.RegionSearchItem;
import com.example.travel.region.service.RegionSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionControllerTest {

	private RegionSearchService regionSearchService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		regionSearchService = mock(RegionSearchService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new RegionController(regionSearchService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsRegionSearchContract() throws Exception {
		when(regionSearchService.search("해운대")).thenReturn(List.of(new RegionSearchItem(
				"KR-26350", "해운대구", "해운대", "부산광역시", "KR-26",
				RegionType.DISTRICT_FILTER, false, true)));

		mockMvc.perform(get("/api/regions").param("query", "해운대"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regions[0].regionId").value("KR-26350"))
				.andExpect(jsonPath("$.regions[0].name").value("해운대구"))
				.andExpect(jsonPath("$.regions[0].shortName").value("해운대"))
				.andExpect(jsonPath("$.regions[0].provinceName").value("부산광역시"))
				.andExpect(jsonPath("$.regions[0].parentRegionId").value("KR-26"))
				.andExpect(jsonPath("$.regions[0].type").value("DISTRICT_FILTER"))
				.andExpect(jsonPath("$.regions[0].selectable").value(false))
				.andExpect(jsonPath("$.regions[0].placeSearchFilterable").value(true));

		verify(regionSearchService).search("해운대");
	}

	@Test
	void returnsEmptyArrayWhenNothingMatches() throws Exception {
		when(regionSearchService.search("없는지역")).thenReturn(List.of());

		mockMvc.perform(get("/api/regions").param("query", "없는지역"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regions").isEmpty());
	}

	@Test
	void rejectsMissingQueryUsingCommonValidationContract() throws Exception {
		mockMvc.perform(get("/api/regions"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("query"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"))
				.andExpect(jsonPath("$.details").value(nullValue()))
				.andExpect(jsonPath("$.adjustments").isEmpty())
				.andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
	}

}
