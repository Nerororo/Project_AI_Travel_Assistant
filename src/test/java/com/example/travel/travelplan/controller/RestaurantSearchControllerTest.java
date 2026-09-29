package com.example.travel.travelplan.controller;

import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.RestaurantSearchApiRequest;
import com.example.travel.travelplan.dto.RestaurantSearchApiResponse;
import com.example.travel.travelplan.service.RestaurantSearchCoordinationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestaurantSearchControllerTest {
	private final RestaurantSearchCoordinationService service = mock(RestaurantSearchCoordinationService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantSearchController(service))
				.setCustomArgumentResolvers(authenticatedUser())
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void callsOnlyCoordinationServiceAndReturnsTemporaryMarkers() throws Exception {
		UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000008");
		when(service.search(eq(7L), eq(requestId), any(RestaurantSearchApiRequest.class)))
				.thenReturn(new RestaurantSearchApiResponse(LocalDate.of(2026, 10, 1),
						LocalTime.NOON, LocalTime.of(13, 0),
						new RestaurantSearchApiResponse.Reference("ATTRACTION", null, 36, 127),
						new RestaurantSearchApiResponse.Reference("END_BOUNDARY", null, 36, 127.02),
						null, List.of(), "NO_CANDIDATES", 1, false));
		mockMvc.perform(post("/api/places/restaurants/search")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mealStartTime").value("12:00"))
				.andExpect(jsonPath("$.emptyReason").value("NO_CANDIDATES"));
		verify(service).search(eq(7L), eq(requestId), any(RestaurantSearchApiRequest.class));
	}

	@Test
	void rejectsMissingIdempotencyKeyAndInvalidNestedDraft() throws Exception {
		mockMvc.perform(post("/api/places/restaurants/search")
					.contentType(MediaType.APPLICATION_JSON).content(validBody()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/places/restaurants/search")
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"estimate\":{},\"mealDate\":\"2026-10-01\",\"mealType\":\"LUNCH\",\"menuQuery\":\"\",\"page\":0,\"size\":16}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	private static String validBody() {
		return """
				{"estimate":{"regionId":"KR-CITY","travelMode":"CAR",
				"startDate":"2026-10-01","endDate":"2026-10-01",
				"startBoundarySelectionToken":"start-token","endBoundarySelectionToken":"end-token",
				"days":[{"date":"2026-10-01","activityStartTime":"09:00",
				"activityEndTime":"20:00"}],
				"places":[{"clientPlaceId":"00000000-0000-0000-0000-000000000001",
				"selectionToken":"visit-token","displayName":"방문","stayMinutes":60,
				"day":"2026-10-01","order":1}],"foods":["메뉴"]},
				"mealDate":"2026-10-01","mealType":"LUNCH","menuQuery":"메뉴",
				"page":1,"size":15}
				""";
	}

	private static HandlerMethodArgumentResolver authenticatedUser() {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
						&& parameter.getParameterType() == AuthenticatedUser.class;
			}

			@Override
			public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
					NativeWebRequest request,
					org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
				return new AuthenticatedUser(7L);
			}
		};
	}
}
