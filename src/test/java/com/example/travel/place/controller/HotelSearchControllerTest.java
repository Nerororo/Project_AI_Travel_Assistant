package com.example.travel.place.controller;

import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.HotelSearchApiRequest;
import com.example.travel.place.dto.HotelSearchApiResponse;
import com.example.travel.place.service.HotelSearchService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelSearchControllerTest {
	private final HotelSearchService service = mock(HotelSearchService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new HotelSearchController(service))
				.setCustomArgumentResolvers(authenticatedUser())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsHotelCandidatesWithoutScoreOrStayTime() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.search(eq(7L), eq(requestId), any(HotelSearchApiRequest.class)))
				.thenReturn(new HotelSearchApiResponse(List.of(new HotelSearchApiResponse.Place(
						"1", URI.create("https://place.map.kakao.com/1"), "provider",
						"address", 36.0, 127.0, "hotel-token")), 1, false));

		mockMvc.perform(post("/api/places/hotels/search")
					.header("Idempotency-Key", requestId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"regionId":"KR-CITY","attractionSelectionTokens":["attraction-token"],
							 "mode":"GEOMETRIC_MEDIAN","page":1,"size":15}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.places[0].selectionToken").value("hotel-token"))
				.andExpect(jsonPath("$.places[0].score").doesNotExist())
				.andExpect(jsonPath("$.places[0].suggestedStayMinutes").doesNotExist())
				.andExpect(jsonPath("$.places[0].providerCategory").doesNotExist());
		verify(service).search(7L, requestId, new HotelSearchApiRequest("KR-CITY",
				List.of("attraction-token"), HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN,
				null, null, 1, 15));
	}

	@Test
	void rejectsMissingRequestIdAndMalformedInput() throws Exception {
		mockMvc.perform(post("/api/places/hotels/search")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"regionId":"KR-CITY","attractionSelectionTokens":["attraction-token"],
							 "mode":"MEDOID","page":1,"size":15}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
		mockMvc.perform(post("/api/places/hotels/search")
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"regionId":"","attractionSelectionTokens":[],
							 "mode":"MAP_BOUNDS","page":0,"size":16}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
