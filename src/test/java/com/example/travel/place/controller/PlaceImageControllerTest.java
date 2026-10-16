package com.example.travel.place.controller;

import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.place.dto.PlaceImageApiRequest;
import com.example.travel.place.dto.PlaceImageApiResponse;
import com.example.travel.place.service.PlaceImageService;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaceImageControllerTest {
	private final PlaceImageService service = mock(PlaceImageService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new PlaceImageController(service))
				.setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
					@Override public boolean supportsParameter(MethodParameter parameter) {
						return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
					}
					@Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
							NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory factory) {
						return new AuthenticatedUser(7L);
					}
				})
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void passesValidatedSearchTermsAndReturnsThumbnails() throws Exception {
		UUID requestId = UUID.randomUUID();
		when(service.search(7L, requestId, new PlaceImageApiRequest("테스트 장소", "테스트도 테스트시")))
				.thenReturn(new PlaceImageApiResponse(List.of(URI.create("https://search2.kakaocdn.net/argon/example"))));
		mvc.perform(post("/api/places/image").header("Idempotency-Key", requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeName\":\"테스트 장소\",\"address\":\"테스트도 테스트시\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.thumbnailUrls[0]").value("https://search2.kakaocdn.net/argon/example"));
		verify(service).search(7L, requestId, new PlaceImageApiRequest("테스트 장소", "테스트도 테스트시"));
	}

	@Test
	void rejectsBlankPlaceName() throws Exception {
		mvc.perform(post("/api/places/image").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeName\":\" \",\"address\":\"테스트도 테스트시\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
