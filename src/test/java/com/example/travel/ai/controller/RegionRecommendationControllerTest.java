package com.example.travel.ai.controller;

import com.example.travel.ai.dto.RegionRecommendationRequest;
import com.example.travel.ai.dto.RegionRecommendationResponse;
import com.example.travel.ai.service.RegionRecommendationService;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionRecommendationControllerTest {

	private RegionRecommendationService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(RegionRecommendationService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new RegionRecommendationController(service))
				.setCustomArgumentResolvers(authenticatedUser(7L))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsRecommendationForAuthenticatedUserAndRequestId() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.recommend(eq(7L), eq(requestId), any(RegionRecommendationRequest.class)))
				.thenReturn(new RegionRecommendationResponse(List.of(
						new RegionRecommendationResponse.RecommendedRegion(
								"KR-51150", "강릉시", "강원특별자치도", "바다 여행에 적합합니다."))));

		mockMvc.perform(post("/api/ai/regions/recommend")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"request\":\" 바다가 있는 곳 \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regions[0].regionId").value("KR-51150"))
				.andExpect(jsonPath("$.regions[0].name").value("강릉시"))
				.andExpect(jsonPath("$.regions[0].provinceName").value("강원특별자치도"));

		verify(service).recommend(7L, requestId, new RegionRecommendationRequest("바다가 있는 곳"));
	}

	@Test
	void rejectsMissingAndMalformedRequestId() throws Exception {
		mockMvc.perform(post("/api/ai/regions/recommend")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"request\":\"여행\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"));

		mockMvc.perform(post("/api/ai/regions/recommend")
					.header("Idempotency-Key", "not-a-uuid")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"request\":\"여행\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_FORMAT"));
	}

	@Test
	void returnsCommonRateLimitContract() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.recommend(eq(7L), eq(requestId), any(RegionRecommendationRequest.class)))
				.thenThrow(new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), 31L));

		mockMvc.perform(post("/api/ai/regions/recommend")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"request\":\"여행\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "31"))
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(31))
				.andExpect(jsonPath("$.details").value(nullValue()));
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
