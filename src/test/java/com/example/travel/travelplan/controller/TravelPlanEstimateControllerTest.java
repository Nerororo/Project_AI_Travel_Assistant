package com.example.travel.travelplan.controller;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.PlanningPlaceSelectionService;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.travelplan.service.TravelPlanEstimateApiService;
import com.example.travel.travelplan.service.TravelPlanEstimateService;
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

import java.net.URI;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelPlanEstimateControllerTest {

	private PlanningPlaceSelectionService places;
	private TravelBoundarySelectionService boundaries;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		places = mock(PlanningPlaceSelectionService.class);
		boundaries = mock(TravelBoundarySelectionService.class);
		TravelPlanEstimateApiService apiService = new TravelPlanEstimateApiService(
				places, new TravelPlanEstimateService(boundaries));
		mockMvc = MockMvcBuilders.standaloneSetup(new TravelPlanEstimateController(apiService))
				.setCustomArgumentResolvers(authenticatedUser(7L))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
		when(places.verifyAttraction("attraction-token", 7L, "KR-30"))
				.thenReturn(new PlanningPlaceSelection("1", placeUrl("1"), 36.0, 127.0));
		when(boundaries.verify("start-token", 7L, "KR-30"))
				.thenReturn(new TravelBoundarySelection("2", placeUrl("2"), 36.0, 127.0));
		when(boundaries.verify("end-token", 7L, "KR-30"))
				.thenReturn(new TravelBoundarySelection("2", placeUrl("2"), 36.0, 127.0));
	}

	@Test
	void returnsTimeOnlyEstimateWithoutProviderData() throws Exception {
		mockMvc.perform(post("/api/travel-plans/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(90, "20:00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routeVerified").value(false))
				.andExpect(jsonPath("$.days[0].date").value("2026-10-01"))
				.andExpect(jsonPath("$.days[0].items[0].startTime")
						.value(matchesPattern("[0-2][0-9]:[0-5][0-9]")))
				.andExpect(jsonPath("$.days[0].plannedEndTime").doesNotExist())
				.andExpect(jsonPath("$.days[0].items[0].mealType").doesNotExist())
				.andExpect(jsonPath("$.days[0].items[0].coordinate").doesNotExist());
	}

	@Test
	void malformedInputAndInvalidSelectionAreBadRequests() throws Exception {
		mockMvc.perform(post("/api/travel-plans/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(90, "20:00").replace("\"foods\":[\"메뉴\"]", "\"foods\":[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		when(places.verifyAttraction("attraction-token", 7L, "KR-30"))
				.thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));
		mockMvc.perform(post("/api/travel-plans/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(90, "20:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.details").value(nullValue()));
	}

	@Test
	void invalidBoundaryIsBadRequestAndCapacityUsesSafe422Details() throws Exception {
		when(boundaries.verify("start-token", 7L, "KR-30"))
				.thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));
		mockMvc.perform(post("/api/travel-plans/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(90, "20:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		doReturn(new TravelBoundarySelection("2", placeUrl("2"), 36.0, 127.0))
				.when(boundaries).verify("start-token", 7L, "KR-30");
		mockMvc.perform(post("/api/travel-plans/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body(480, "12:00")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("PLAN_CAPACITY_EXCEEDED"))
				.andExpect(jsonPath("$.details.date").value("2026-10-01"))
				.andExpect(jsonPath("$.details.allowedEndTime").value("12:00"))
				.andExpect(jsonPath("$.details.exceededMinutes").isNumber())
				.andExpect(jsonPath("$.days").doesNotExist());
	}

	private static URI placeUrl(String id) {
		return URI.create("https://place.map.kakao.com/" + id);
	}

	private static String body(int stayMinutes, String endTime) {
		return """
				{
				  "regionId":"KR-30",
				  "travelMode":"CAR",
				  "startDate":"2026-10-01",
				  "endDate":"2026-10-01",
				  "startBoundarySelectionToken":"start-token",
				  "endBoundarySelectionToken":"end-token",
				  "days":[{"date":"2026-10-01","activityStartTime":"09:00","activityEndTime":"%s"}],
				  "places":[{"clientPlaceId":"00000000-0000-0000-0000-000000000001",
				    "selectionToken":"attraction-token","displayName":"사용자 이름",
				    "stayMinutes":%d,"day":null,"order":null}],
				  "hotelSelectionToken":null,
				  "mealTravelBufferMinutes":15,
				  "foods":["메뉴"]
				}
				""".formatted(endTime, stayMinutes);
	}

	private static HandlerMethodArgumentResolver authenticatedUser(long userId) {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
						&& parameter.getParameterType() == AuthenticatedUser.class;
			}

			@Override
			public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
					NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
				return new AuthenticatedUser(userId);
			}
		};
	}
}
