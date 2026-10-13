package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@Import(AuthAttemptLimitIntegrationTest.FixedClock.class)
class AuthAttemptLimitIntegrationTest {
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
	private AuthAttemptLimitService service;
	@Autowired
	private AuthAttemptTransaction transaction;
	@Autowired
	private JwtProperties jwtProperties;

	@Test
	void loginAndRegistrationEnforceBothIpAndAccountLimits() {
		for (int index = 0; index < 5; index++) {
			service.acquireLogin("203.0.113." + (index + 10), "login-account@example.test");
		}
		assertRateLimited(() -> service.acquireLogin("203.0.113.20", "login-account@example.test"));

		for (int index = 0; index < 30; index++) {
			service.acquireLogin("203.0.113.30", "login-ip-" + index + "@example.test");
		}
		assertRateLimited(() -> service.acquireLogin("203.0.113.30", "login-ip-extra@example.test"));

		for (int index = 0; index < 3; index++) {
			service.acquireRegistration("198.51.100." + (index + 10), "register-account@example.test");
		}
		assertRateLimited(() -> service.acquireRegistration("198.51.100.20", "register-account@example.test"));

		for (int index = 0; index < 5; index++) {
			service.acquireRegistration("198.51.100.30", "register-ip-" + index + "@example.test");
		}
		assertRateLimited(() -> service.acquireRegistration("198.51.100.30", "register-ip-extra@example.test"));
	}

	@Test
	void nextWindowAllowsRetryAfterAccountLimit() {
		Instant nearEnd = Instant.parse("2026-10-12T00:09:59Z");
		AuthAttemptLimitService currentWindow = serviceAt(nearEnd);
		for (int index = 0; index < 5; index++) {
			currentWindow.acquireLogin("192.0.2." + (index + 10), "window@example.test");
		}
		assertRateLimited(() -> currentWindow.acquireLogin("192.0.2.20", "window@example.test"));
		serviceAt(nearEnd.plusSeconds(1)).acquireLogin("192.0.2.20", "window@example.test");
	}

	private AuthAttemptLimitService serviceAt(Instant instant) {
		return new AuthAttemptLimitService(transaction, Clock.fixed(instant, ZoneOffset.UTC), jwtProperties);
	}

	private static void assertRateLimited(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
	}

	@Test
	void concurrentCallsAcrossTransactionsCannotExceedAccountLimit() throws Exception {
		int requests = 12;
		CountDownLatch ready = new CountDownLatch(requests);
		CountDownLatch start = new CountDownLatch(1);
		var results = new ArrayList<Boolean>();
		try (var executor = Executors.newFixedThreadPool(requests)) {
			var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
			for (int index = 0; index < requests; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						service.acquireLogin("203.0.113.4", "parallel@example.test");
						return true;
					} catch (ApiException exception) {
						assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
						return false;
					}
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (var future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
		}
		assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(5);
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	@TestConfiguration
	static class FixedClock {
		@Bean
		@Primary
		Clock authAttemptTestClock() {
			return Clock.fixed(Instant.parse("2026-10-12T00:00:01Z"), ZoneOffset.UTC);
		}
	}
}
