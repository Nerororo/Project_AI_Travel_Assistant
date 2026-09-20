package com.example.travel.place.controller;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.PlaceSearchApiRequest;
import com.example.travel.place.dto.PlaceSearchApiResponse;
import com.example.travel.place.service.PlaceSearchService;
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

class PlaceSearchControllerTest {

	private PlaceSearchService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(PlaceSearchService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new PlaceSearchController(service))
				.setCustomArgumentResolvers(authenticatedUser(7L))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsTransientCandidatesForAuthenticatedUserAndRequestId() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.search(eq(7L), eq(requestId), any(PlaceSearchApiRequest.class)))
				.thenReturn(new PlaceSearchApiResponse(List.of(new PlaceSearchApiResponse.Place(
						"1", URI.create("https://place.map.kakao.com/1"), "provider", "address",
						36.1, 127.1, 90, "signed-token")), 1, false));

		mockMvc.perform(post("/api/places/search")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"regionId":"KR-CITY","districtFilterId":null,"placeRole":"ATTRACTION",
							 "query":" museum ","center":null,"radiusMeters":20000,"page":1,"size":15}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.places[0].kakaoPlaceId").value("1"))
				.andExpect(jsonPath("$.places[0].suggestedStayMinutes").value(90))
				.andExpect(jsonPath("$.places[0].selectionToken").value("signed-token"))
				.andExpect(jsonPath("$.places[0].placeRole").doesNotExist())
				.andExpect(jsonPath("$.places[0].providerCategory").doesNotExist());

		verify(service).search(7L, requestId, new PlaceSearchApiRequest(
				"KR-CITY", null, com.example.travel.place.domain.PlaceRole.ATTRACTION,
				"museum", null, 20_000, 1, 15));
	}

	@Test
	void rejectsMissingRequestIdAndInvalidBody() throws Exception {
		mockMvc.perform(post("/api/places/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"regionId":"KR-CITY","placeRole":"ATTRACTION","query":"museum",
							 "radiusMeters":20000,"page":1,"size":15}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));

		mockMvc.perform(post("/api/places/search")
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"regionId":"","placeRole":"ATTRACTION","query":" ",
							 "radiusMeters":20001,"page":0,"size":16}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void returnsCommonRateLimitAndProviderFailureContracts() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.search(eq(7L), eq(requestId), any(PlaceSearchApiRequest.class)))
				.thenThrow(new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), 31L));

		mockMvc.perform(validRequest(requestId))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "31"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(31));

		when(service.search(eq(7L), eq(requestId), any(PlaceSearchApiRequest.class)))
				.thenThrow(new ApiException(ErrorCode.PLACE_PROVIDER_UNAVAILABLE));
		mockMvc.perform(validRequest(requestId))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("PLACE_PROVIDER_UNAVAILABLE"))
				.andExpect(jsonPath("$.details").value(nullValue()));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(UUID requestId) {
		return post("/api/places/search")
				.header("Idempotency-Key", requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"regionId":"KR-CITY","placeRole":"ATTRACTION","query":"museum",
						 "radiusMeters":20000,"page":1,"size":15}
						""");
	}

	private static HandlerMethodArgumentResolver authenticatedUser(long userId) {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
						&& parameter.getParameterType() == AuthenticatedUser.class;
			}

			@Override
			public Object resolveArgument(
					MethodParameter parameter,
					ModelAndViewContainer container,
					NativeWebRequest request,
					org.springframework.web.bind.support.WebDataBinderFactory binderFactory
			) {
				return new AuthenticatedUser(userId);
			}
		};
	}
}
