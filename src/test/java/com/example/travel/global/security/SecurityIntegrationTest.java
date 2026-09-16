package com.example.travel.global.security;

import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import com.example.travel.user.service.UserRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.Base64;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

	private static final String JWT_TEST_SECRET = randomSecret();

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		registry.add("routy.jwt.active-key-id", () -> "test-active");
		registry.add("routy.jwt.keys[0].id", () -> "test-active");
		registry.add("routy.jwt.keys[0].secret", () -> JWT_TEST_SECRET);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRegistrationService registrationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
	}

	@Test
	void blocksProtectedApiWithoutBearerTokenUsingCommonErrorContract() throws Exception {
		mockMvc.perform(get("/api/protected-probe"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("인증이 필요합니다."))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(jsonPath("$.details").value(nullValue()))
				.andExpect(jsonPath("$.adjustments").isEmpty())
				.andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
	}

	@Test
	void acceptsValidTokenForExistingUserAndRejectsChangedSignature() throws Exception {
		registrationService.register("security-member@example.test", "p".repeat(12));
		User user = userRepository.findByEmail("security-member@example.test").orElseThrow();
		String token = jwtService.issue(user.id());

		mockMvc.perform(get("/api/protected-probe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());

		int signatureStart = token.lastIndexOf('.') + 1;
		char signatureCharacter = token.charAt(signatureStart);
		String tampered = token.substring(0, signatureStart) + (signatureCharacter == 'A' ? 'B' : 'A')
				+ token.substring(signatureStart + 1);
		mockMvc.perform(get("/api/protected-probe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void rejectsValidlySignedTokenAfterUserNoLongerExists() throws Exception {
		registrationService.register("deleted-member@example.test", "p".repeat(12));
		User user = userRepository.findByEmail("deleted-member@example.test").orElseThrow();
		String token = jwtService.issue(user.id());
		userRepository.delete(user);
		userRepository.flush();

		mockMvc.perform(get("/api/protected-probe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void publicRegistrationAndLoginRemainAccessibleWithoutToken() throws Exception {
		mockMvc.perform(post("/api/users")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/shared/travel-plans/public-token"))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/users"))
				.andExpect(status().isUnauthorized());
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
