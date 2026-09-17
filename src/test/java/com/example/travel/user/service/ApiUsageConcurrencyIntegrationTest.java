package com.example.travel.user.service;

import com.example.travel.user.domain.ApiUsageCounter;
import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.repository.ApiUsageCounterRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = ApiUsageConcurrencyIntegrationTest.ClockTestConfig.class)
class ApiUsageConcurrencyIntegrationTest {

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
	private ApiUsageService usageService;

	@Autowired
	private ApiUsageCounterRepository counterRepository;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		counterRepository.deleteAll();
		clock.set(Instant.parse("2026-09-16T00:00:00Z"));
	}

	@Test
	void concurrentInstancesNeverAcquireMoreThanTheLimit() throws Exception {
		int attempts = 12;
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = new ArrayList<>();

		try (var executor = Executors.newFixedThreadPool(attempts)) {
			for (int index = 0; index < attempts; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return usageService.tryAcquire(77L, UsageFeature.AI_REGION_RECOMMENDATION, 1).acquired();
				}));
			}
			ready.await();
			start.countDown();

			int acquired = 0;
			for (Future<Boolean> future : futures) {
				if (future.get()) {
					acquired++;
				}
			}
			assertThat(acquired).isEqualTo(2);
		}

		assertThat(counter(UsageScopeType.USER, "77", UsageFeature.AI_REGION_RECOMMENDATION,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(2L);
		assertThat(counter(UsageScopeType.USER, "77", UsageFeature.AI_REGION_RECOMMENDATION,
				UsageWindowType.DAY).usedCount()).isEqualTo(2L);
	}

	@Test
	void regionAiAllowsTwoPerMinuteAndTenPerDayThenRejectsTheNextCall() {
		assertCallsAllowed(41L, UsageFeature.AI_REGION_RECOMMENDATION, 2);
		assertThat(usageService.tryAcquire(41L, UsageFeature.AI_REGION_RECOMMENDATION, 1).acquired())
				.isFalse();

		for (int minute = 1; minute < 5; minute++) {
			clock.set(Instant.parse("2026-09-16T00:0" + minute + ":00Z"));
			assertCallsAllowed(41L, UsageFeature.AI_REGION_RECOMMENDATION, 2);
		}
		clock.set(Instant.parse("2026-09-16T00:05:00Z"));

		assertThat(usageService.tryAcquire(41L, UsageFeature.AI_REGION_RECOMMENDATION, 1).acquired())
				.isFalse();
		assertThat(counter(UsageScopeType.USER, "41", UsageFeature.AI_REGION_RECOMMENDATION,
				UsageWindowType.DAY).usedCount()).isEqualTo(10L);
	}

	@Test
	void menuAiAllowsThreePerMinuteAndFifteenPerDayThenRejectsTheNextCall() {
		assertCallsAllowed(42L, UsageFeature.AI_MENU_ANALYSIS, 3);
		assertThat(usageService.tryAcquire(42L, UsageFeature.AI_MENU_ANALYSIS, 1).acquired())
				.isFalse();

		for (int minute = 1; minute < 5; minute++) {
			clock.set(Instant.parse("2026-09-16T00:0" + minute + ":00Z"));
			assertCallsAllowed(42L, UsageFeature.AI_MENU_ANALYSIS, 3);
		}
		clock.set(Instant.parse("2026-09-16T00:05:00Z"));

		assertThat(usageService.tryAcquire(42L, UsageFeature.AI_MENU_ANALYSIS, 1).acquired())
				.isFalse();
		assertThat(counter(UsageScopeType.USER, "42", UsageFeature.AI_MENU_ANALYSIS,
				UsageWindowType.DAY).usedCount()).isEqualTo(15L);
	}

	@Test
	void failedServiceLimitReservationRollsBackEveryUserWindow() {
		for (long userId = 1; userId <= 8; userId++) {
			assertThat(usageService.tryAcquire(userId, UsageFeature.PUBLIC_TRANSIT_ROUTE, 60).acquired()).isTrue();
		}
		clock.set(Instant.parse("2026-09-16T00:01:00Z"));
		for (long userId = 1; userId <= 7; userId++) {
			assertThat(usageService.tryAcquire(userId, UsageFeature.PUBLIC_TRANSIT_ROUTE, 60).acquired()).isTrue();
		}

		var denied = usageService.tryAcquire(8L, UsageFeature.PUBLIC_TRANSIT_ROUTE, 60);

		assertThat(denied.acquired()).isFalse();
		assertThat(denied.retryAfterSeconds()).isEqualTo(53_940L);
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(900L);
		assertThat(counter(UsageScopeType.USER, "8", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(60L);
		assertThat(counter(UsageScopeType.USER, "8", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(60L);
		assertThat(isActualTransactionActive()).isFalse();
	}

	@Test
	void dailyWindowChangesAtSeoulMidnight() {
		clock.set(Instant.parse("2026-09-16T14:59:59Z"));
		assertThat(usageService.tryAcquire(31L, UsageFeature.AI_MENU_ANALYSIS, 3).acquired()).isTrue();

		clock.set(Instant.parse("2026-09-16T15:00:00Z"));
		assertThat(usageService.tryAcquire(31L, UsageFeature.AI_MENU_ANALYSIS, 3).acquired()).isTrue();

		List<ApiUsageCounter> daily = counterRepository.findAll().stream()
				.filter(counter -> counter.scopeType() == UsageScopeType.USER)
				.filter(counter -> counter.windowType() == UsageWindowType.DAY)
				.filter(counter -> counter.feature() == UsageFeature.AI_MENU_ANALYSIS)
				.toList();
		assertThat(daily).extracting(ApiUsageCounter::windowStart)
				.containsExactlyInAnyOrder(
						Instant.parse("2026-09-15T15:00:00Z"),
						Instant.parse("2026-09-16T15:00:00Z"));
	}

	private ApiUsageCounter counter(UsageScopeType scopeType, String scopeId, UsageFeature feature,
			UsageWindowType windowType) {
		return counterRepository.findAll().stream()
				.filter(counter -> counter.scopeType() == scopeType)
				.filter(counter -> counter.scopeId().equals(scopeId))
				.filter(counter -> counter.feature() == feature)
				.filter(counter -> counter.windowType() == windowType)
				.findFirst()
				.orElseThrow();
	}

	private void assertCallsAllowed(long userId, UsageFeature feature, int count) {
		for (int call = 0; call < count; call++) {
			assertThat(usageService.tryAcquire(userId, feature, 1).acquired()).isTrue();
		}
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
