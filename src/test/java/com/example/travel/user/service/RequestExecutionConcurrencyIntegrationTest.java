package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.domain.RequestExecutionStatus;
import com.example.travel.user.domain.User;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.repository.ApiUsageCounterRepository;
import com.example.travel.user.repository.RequestExecutionRepository;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = RequestExecutionConcurrencyIntegrationTest.ClockTestConfig.class)
class RequestExecutionConcurrencyIntegrationTest {

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
	private RequestExecutionService service;

	@Autowired
	private RequestExecutionRepository executionRepository;

	@Autowired
	private ApiUsageCounterRepository usageRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MutableClock clock;

	private long userId;

	@BeforeEach
	void setUp() {
		executionRepository.deleteAll();
		usageRepository.deleteAll();
		userRepository.deleteAll();
		userId = userRepository.saveAndFlush(new User("execution-owner@example.test", "stored-hash")).id();
		clock.set(Instant.parse("2026-09-16T00:00:00Z"));
	}

	@Test
	void processingAndSuccessDuplicatesAreBlockedBeforeUsageIsChargedAgain() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1).lease();

		assertDuplicate(requestId, ErrorCode.REQUEST_IN_PROGRESS);
		assertThat(totalUsage()).isEqualTo(3L);
		assertThat(service.markSucceeded(lease)).isTrue();
		assertDuplicate(requestId, ErrorCode.REQUEST_ALREADY_COMPLETED);
		assertThat(totalUsage()).isEqualTo(3L);
	}

	@Test
	void failureReleaseAllowsRetryWhileKeepingAlreadyChargedUsage() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease first = service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1).lease();

		assertThat(service.releaseAfterFailure(first)).isTrue();
		RequestExecutionLease second = service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1).lease();

		assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
		assertThat(totalUsage()).isEqualTo(6L);
	}

	@Test
	void expiryAllowsNewExecutionAndOldLeaseCannotChangeIt() {
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease oldLease = service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1).lease();
		clock.set(Instant.parse("2026-09-16T00:09:59Z"));
		assertDuplicate(requestId, ErrorCode.REQUEST_IN_PROGRESS);

		clock.set(Instant.parse("2026-09-16T00:10:00Z"));
		RequestExecutionLease newLease = service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1).lease();

		assertThat(newLease.expiresAt()).isEqualTo(Instant.parse("2026-09-16T00:20:00Z"));
		assertThat(service.markSucceeded(oldLease)).isFalse();
		assertThat(service.releaseAfterFailure(oldLease)).isFalse();
		assertThat(executionRepository.findAll()).singleElement()
				.extracting(execution -> execution.status(), execution -> execution.expiresAt())
				.containsExactly(RequestExecutionStatus.PROCESSING, newLease.expiresAt());
	}

	@Test
	void cleanupDeletesOnlyRequestedExpiredBatchSize() {
		for (int index = 0; index < 3; index++) {
			service.tryStart(userId, UsageFeature.PLACE_SEARCH, UUID.randomUUID(), 1);
		}
		clock.set(Instant.parse("2026-09-16T00:10:00Z"));

		assertThat(service.deleteExpired(2)).isEqualTo(2);
		assertThat(executionRepository.count()).isEqualTo(1);
		assertThat(service.deleteExpired(2)).isEqualTo(1);
		assertThat(executionRepository.count()).isZero();
	}

	@Test
	void concurrentInstancesStartExactlyOneExecution() throws Exception {
		UUID requestId = UUID.randomUUID();
		int attempts = 12;
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ErrorCode>> futures = new ArrayList<>();

		try (var executor = Executors.newFixedThreadPool(attempts)) {
			for (int index = 0; index < attempts; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1);
						return null;
					} catch (ApiException exception) {
						return exception.errorCode();
					}
				}));
			}
			ready.await();
			start.countDown();

			List<ErrorCode> outcomes = new ArrayList<>();
			for (Future<ErrorCode> future : futures) {
				outcomes.add(future.get());
			}
			assertThat(outcomes).containsOnlyOnce((ErrorCode) null);
			assertThat(outcomes.stream().filter(ErrorCode.REQUEST_IN_PROGRESS::equals)).hasSize(11);
		}

		assertThat(executionRepository.count()).isEqualTo(1);
		assertThat(totalUsage()).isEqualTo(3L);
	}

	private void assertDuplicate(UUID requestId, ErrorCode expected) {
		assertThatThrownBy(() -> service.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(expected));
	}

	private long totalUsage() {
		return usageRepository.findAll().stream().mapToLong(counter -> counter.usedCount()).sum();
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	@TestConfiguration
	static class ClockTestConfig {
		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-09-16T00:00:00Z"));
		}
	}

	static final class MutableClock extends Clock {
		private volatile Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
