package com.example.travel.region.controller;

import com.example.travel.global.security.JwtService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class RegionControllerSecurityIntegrationTest {

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
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/regions").param("query", "강릉"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void returnsRegionsForAuthenticatedUser() throws Exception {
		String token = authenticatedToken();

		mockMvc.perform(get("/api/regions")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.param("query", "강릉"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regions[0].regionId").value("KR-51150"));
	}

	@Test
	void rejectsBlankQueryForAuthenticatedUser() throws Exception {
		String token = authenticatedToken();

		mockMvc.perform(get("/api/regions")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.param("query", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("query"))
				.andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"));
	}

	private String authenticatedToken() {
		registrationService.register("region-member@example.test", "p".repeat(12));
		User user = userRepository.findByEmail("region-member@example.test").orElseThrow();
		return jwtService.issue(user.id());
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
