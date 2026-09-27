package com.example.travel.user.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.CarRouteClient;
import com.example.travel.route.client.PublicTransitRouteClient;
import com.example.travel.route.client.RouteClientException;
import com.example.travel.route.client.RouteClientFailure;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.route.service.RouteQuotaService;
import com.example.travel.route.service.RouteService;
import com.example.travel.route.service.RouteVerificationService;
import com.example.travel.user.domain.ApiUsageCounter;
import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageDenialScope;
import com.example.travel.user.dto.UsageReservationLease;
import com.example.travel.user.repository.ApiUsageCounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
	private JdbcTemplate jdbcTemplate;

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
		assertThat(denied.deniedScopes()).containsExactly(UsageDenialScope.SERVICE);
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(900L);
		assertThat(counter(UsageScopeType.USER, "8", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(60L);
		assertThat(counter(UsageScopeType.USER, "8", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(60L);
		assertThat(isActualTransactionActive()).isFalse();
	}

	@Test
	void reservesTheWholeCarRouteCandidateAtomicallyAndEndsTheTransaction() {
		assertThat(usageService.tryAcquire(51L, UsageFeature.CAR_ROUTE, 59).acquired()).isTrue();

		var denied = usageService.tryAcquire(51L, UsageFeature.CAR_ROUTE, 2);

		assertThat(denied.acquired()).isFalse();
		assertThat(denied.deniedScopes()).containsExactly(UsageDenialScope.USER);
		assertThat(counter(UsageScopeType.USER, "51", UsageFeature.CAR_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(59L);
		assertThat(counter(UsageScopeType.USER, "51", UsageFeature.CAR_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(59L);
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.CAR_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(59L);
		assertThat(isActualTransactionActive()).isFalse();
	}

	@Test
	void reportsUserAndServiceWhenBothScopesDenyTheSameReservation() {
		for (long userId = 1; userId <= 15; userId++) {
			assertThat(usageService.tryAcquire(
					userId,
					UsageFeature.PUBLIC_TRANSIT_ROUTE,
					60
			).acquired()).isTrue();
		}

		var denied = usageService.tryAcquire(1L, UsageFeature.PUBLIC_TRANSIT_ROUTE, 1);

		assertThat(denied.acquired()).isFalse();
		assertThat(denied.deniedScopes())
				.containsExactlyInAnyOrder(UsageDenialScope.USER, UsageDenialScope.SERVICE);
		assertThat(denied.retryAfterSeconds()).isEqualTo(54_000L);
		assertThat(counter(UsageScopeType.USER, "1", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(60L);
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(900L);
	}

	@Test
	void releasesUnusedRouteReservationsAcrossEveryScopeAtomically() {
		UsageReservationLease reservation = usageService.reserve(
				91L,
				UsageFeature.PUBLIC_TRANSIT_ROUTE,
				5
		);
		assertThat(reservation.result().acquired()).isTrue();

		usageService.release(91L, UsageFeature.PUBLIC_TRANSIT_ROUTE, reservation, 2);

		assertThat(counter(UsageScopeType.USER, "91", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(3L);
		assertThat(counter(UsageScopeType.USER, "91", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(3L);
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(3L);
		assertThat(isActualTransactionActive()).isFalse();
	}

	@Test
	void releaseUsesTheOriginalWindowsAfterTheMinuteBoundaryChanges() {
		clock.set(Instant.parse("2026-09-16T00:00:59Z"));
		UsageReservationLease reservation = usageService.reserve(
				93L,
				UsageFeature.CAR_ROUTE,
				3
		);
		clock.set(Instant.parse("2026-09-16T00:01:00Z"));

		usageService.release(93L, UsageFeature.CAR_ROUTE, reservation, 2);

		assertThat(counter(UsageScopeType.USER, "93", UsageFeature.CAR_ROUTE,
				UsageWindowType.MINUTE).windowStart())
				.isEqualTo(Instant.parse("2026-09-16T00:00:00Z"));
		assertThat(counter(UsageScopeType.USER, "93", UsageFeature.CAR_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(1L);
	}

	@Test
	void failedReleaseRollsBackEveryEarlierCounterDecrease() {
		UsageReservationLease reservation = usageService.reserve(
				94L,
				UsageFeature.PUBLIC_TRANSIT_ROUTE,
				5
		);
		int changed = jdbcTemplate.update("""
				UPDATE api_usage_counters
				SET used_count = 0
				WHERE scope_type = 'USER'
				  AND scope_id = '94'
				  AND feature = 'PUBLIC_TRANSIT_ROUTE'
				  AND window_type = 'DAY'
				""");
		assertThat(changed).isEqualTo(1);

		assertThatThrownBy(() -> usageService.release(
				94L,
				UsageFeature.PUBLIC_TRANSIT_ROUTE,
				reservation,
				2
		)).isInstanceOf(IllegalStateException.class);

		assertThat(counter(UsageScopeType.USER, "94", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(5L);
		assertThat(counter(UsageScopeType.USER, "94", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isZero();
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.PUBLIC_TRANSIT_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(5L);
	}

	@Test
	void providerCallsRunAfterTransactionsAndMatchTheFinalCounterAfterEarlyFallback() {
		AtomicInteger providerCalls = new AtomicInteger();
		CarRouteClient carClient = segment -> {
			assertThat(isActualTransactionActive()).isFalse();
			providerCalls.incrementAndGet();
			throw new RouteClientException(RouteClientFailure.TIMEOUT);
		};
		PublicTransitRouteClient publicTransitClient = segment -> RouteResult.notFound();
		RouteVerificationService routeVerificationService = new RouteVerificationService(
				new RouteQuotaService(usageService),
				new RouteService(carClient, publicTransitClient)
		);
		List<RouteSegment> segments = List.of(
				segment(0.0, 0.0, 1.0, 1.0),
				segment(1.0, 1.0, 2.0, 2.0)
		);

		var result = routeVerificationService.verify(92L, TravelMode.CAR, segments);

		assertThat(result.travelTimes().fallbackApplied()).isTrue();
		assertThat(providerCalls).hasValue(2);
		assertThat(counter(UsageScopeType.USER, "92", UsageFeature.CAR_ROUTE,
				UsageWindowType.MINUTE).usedCount()).isEqualTo(2L);
		assertThat(counter(UsageScopeType.USER, "92", UsageFeature.CAR_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(2L);
		assertThat(counter(UsageScopeType.SERVICE, "GLOBAL", UsageFeature.CAR_ROUTE,
				UsageWindowType.DAY).usedCount()).isEqualTo(2L);
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

	private RouteSegment segment(double originLatitude, double originLongitude,
			double destinationLatitude, double destinationLongitude) {
		return new RouteSegment(
				new RouteSegment.Endpoint(originLatitude, originLongitude),
				new RouteSegment.Endpoint(destinationLatitude, destinationLongitude)
		);
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
