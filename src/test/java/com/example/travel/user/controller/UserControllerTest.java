package com.example.travel.user.controller;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.user.service.UserRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

	private UserRegistrationService userRegistrationService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		userRegistrationService = mock(UserRegistrationService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userRegistrationService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void createsUserWithoutResponseBody() throws Exception {
		String password = validPassword();

		mockMvc.perform(post("/api/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("MEMBER@EXAMPLE.TEST", password)))
				.andExpect(status().isCreated())
				.andExpect(content().string(""));

		verify(userRegistrationService).register("MEMBER@EXAMPLE.TEST", password);
	}

	@Test
	void rejectsInvalidEmailWithStableValidationResponse() throws Exception {
		mockMvc.perform(post("/api/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("invalid-email", validPassword())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_FORMAT"))
				.andExpect(jsonPath("$.details").value(nullValue()))
				.andExpect(jsonPath("$.adjustments").isEmpty())
				.andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
	}

	@Test
	void rejectsPasswordOutsideCodePointAndUtf8ByteLimits() throws Exception {
		mockMvc.perform(post("/api/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", "가".repeat(25))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_SIZE"));
	}

	@Test
	void rejectsPasswordLongerThanSixtyFourCodePoints() throws Exception {
		mockMvc.perform(post("/api/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", "p".repeat(65))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_SIZE"));
	}

	@Test
	void rejectsPasswordContainingControlCharacter() throws Exception {
		mockMvc.perform(post("/api/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", "validpass\\nword")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("INVALID_FORMAT"));
	}

	@Test
	void returnsConflictWithoutExposingSubmittedValuesForDuplicateEmail() throws Exception {
		doThrow(new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS))
				.when(userRegistrationService).register(anyString(), anyString());

		mockMvc.perform(post("/api/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson("member@example.test", validPassword())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(jsonPath("$.details").value(nullValue()))
				.andExpect(jsonPath("$.adjustments").isEmpty())
				.andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
	}

	private static String validPassword() {
		return "p".repeat(12);
	}

	private static String requestJson(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}
}
