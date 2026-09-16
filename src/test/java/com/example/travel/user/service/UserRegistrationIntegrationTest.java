package com.example.travel.user.service;

import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class UserRegistrationIntegrationTest {

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
	private UserRegistrationService userRegistrationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void storesNormalizedEmailAndBcryptHashInMySql() {
		String password = "p".repeat(12);

		userRegistrationService.register("MEMBER@EXAMPLE.TEST", password);

		User saved = userRepository.findByEmail("member@example.test").orElseThrow();
		assertThat(saved.passwordHash()).isNotEqualTo(password);
		assertThat(saved.passwordHash()).startsWith("$2");
		assertThat(passwordEncoder.matches(password, saved.passwordHash())).isTrue();
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
