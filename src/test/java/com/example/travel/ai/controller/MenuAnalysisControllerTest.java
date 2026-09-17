package com.example.travel.ai.controller;

import com.example.travel.ai.dto.MenuAnalysisRequest;
import com.example.travel.ai.dto.MenuAnalysisResponse;
import com.example.travel.ai.service.MenuAnalysisService;
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

class MenuAnalysisControllerTest {

	private MenuAnalysisService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(MenuAnalysisService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new MenuAnalysisController(service))
				.setCustomArgumentResolvers(authenticatedUser(7L))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsStructuredMenusForAuthenticatedUserAndRequestId() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.analyze(eq(7L), eq(requestId), any(MenuAnalysisRequest.class)))
				.thenReturn(new MenuAnalysisResponse(List.of(new MenuAnalysisResponse.Menu(
						"돼지국밥", "돼지국밥", "부산 지역 음식", "browser-place"))));

		mockMvc.perform(post("/api/ai/menus/analyze")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(validBody()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.menus[0].name").value("돼지국밥"))
				.andExpect(jsonPath("$.menus[0].searchQuery").value("돼지국밥"))
				.andExpect(jsonPath("$.menus[0].targetClientPlaceId").value("browser-place"));

		verify(service).analyze(eq(7L), eq(requestId), any(MenuAnalysisRequest.class));
	}

	@Test
	void rejectsMissingOrMalformedRequestIdAndInvalidBody() throws Exception {
		mockMvc.perform(post("/api/ai/menus/analyze")
					.contentType(MediaType.APPLICATION_JSON)
					.content(validBody()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"));

		mockMvc.perform(post("/api/ai/menus/analyze")
					.header("Idempotency-Key", "not-a-uuid")
					.contentType(MediaType.APPLICATION_JSON)
					.content(validBody()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_FORMAT"));

		mockMvc.perform(post("/api/ai/menus/analyze")
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"regionId\":\" \",\"request\":\" \",\"attractions\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void exposesSafeInvalidAiAndRateLimitContractsForDirectInputFallback() throws Exception {
		UUID invalidRequestId = UUID.randomUUID();
		when(service.analyze(eq(7L), eq(invalidRequestId), any(MenuAnalysisRequest.class)))
				.thenThrow(new ApiException(ErrorCode.AI_RESPONSE_INVALID));

		mockMvc.perform(post("/api/ai/menus/analyze")
					.header("Idempotency-Key", invalidRequestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(validBody()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_RESPONSE_INVALID"))
				.andExpect(jsonPath("$.message").value("AI 응답을 처리할 수 없습니다."))
				.andExpect(jsonPath("$.details").value(nullValue()));

		UUID limitedRequestId = UUID.randomUUID();
		when(service.analyze(eq(7L), eq(limitedRequestId), any(MenuAnalysisRequest.class)))
				.thenThrow(new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), 18L));

		mockMvc.perform(post("/api/ai/menus/analyze")
					.header("Idempotency-Key", limitedRequestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(validBody()))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "18"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(18));
	}

	@Test
	void returnsValidationErrorForNonSelectableRegion() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.analyze(eq(7L), eq(requestId), any(MenuAnalysisRequest.class)))
				.thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));

		mockMvc.perform(post("/api/ai/menus/analyze")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(validBody()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.details").value(nullValue()));
	}

	private static String validBody() {
		return """
				{
				  "regionId":"KR-26",
				  "request":"부산다운 음식",
				  "attractions":[{"clientPlaceId":"browser-place","displayName":"사용자 장소"}]
				}
				""";
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
