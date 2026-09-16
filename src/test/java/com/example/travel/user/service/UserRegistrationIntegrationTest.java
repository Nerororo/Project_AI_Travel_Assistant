package com.example.travel.user.service;

import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class UserRegistrationIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

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
}
