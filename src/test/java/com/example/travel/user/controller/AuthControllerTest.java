package com.example.travel.user.controller;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.user.dto.LoginResponse;
import com.example.travel.user.service.LoginService;
import com.example.travel.global.security.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

	private LoginService loginService;
	private ClientIpResolver clientIpResolver;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		loginService = mock(LoginService.class);
		clientIpResolver = mock(ClientIpResolver.class);
		when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");
		mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(loginService, clientIpResolver))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void returnsAccessTokenForValidCredentials() throws Exception {
		when(loginService.login("member@example.test", "submitted-password", "127.0.0.1"))
				.thenReturn(new LoginResponse("issued-token", "Bearer", 3600));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", "submitted-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("issued-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresInSeconds").value(3600));

		verify(loginService).login("member@example.test", "submitted-password", "127.0.0.1");
	}

	@Test
	void returnsSameAuthenticationErrorForInvalidCredentials() throws Exception {
		doThrow(new ApiException(ErrorCode.AUTHENTICATION_REQUIRED))
				.when(loginService).login("missing@example.test", "submitted-password", "127.0.0.1");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("missing@example.test", "submitted-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(jsonPath("$.details").value(nullValue()))
				.andExpect(jsonPath("$.adjustments").isEmpty())
				.andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
	}

	@Test
	void rejectsPasswordOutsideContractBeforeCallingService() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_SIZE"));
	}

	@Test
	void returnsRetryAfterWhenLoginLimitIsReached() throws Exception {
		doThrow(new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, java.util.List.of(), 42L))
				.when(loginService).login("member@example.test", "submitted-password", "127.0.0.1");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", "submitted-password")))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "42"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(42));
	}

	private static String requestJson(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}
}
