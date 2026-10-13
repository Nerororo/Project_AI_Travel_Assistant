package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.security.JwtProperties;
import com.example.travel.user.repository.AuthAttemptCounterRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAttemptLimitServiceTest {
	private final AuthAttemptCounterRepository counters = mock(AuthAttemptCounterRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:01.500Z"), ZoneOffset.UTC);
	private final JwtProperties properties = new JwtProperties("routy", Duration.ofHours(1), "active",
			List.of(new JwtProperties.SigningKey("active", Base64.getEncoder().encodeToString(new byte[32]))));
	private final AuthAttemptLimitService service = new AuthAttemptLimitService(new AuthAttemptTransaction(counters), clock, properties);

	@Test
	void loginUsesDistinctHashedIpAndAccountBuckets() {
		when(counters.acquire(anyString(), anyString(), eq(Instant.parse("2026-10-12T00:00:00Z")),
				eq(Instant.parse("2026-10-12T00:10:00Z")), eq(30))).thenReturn(true);
		when(counters.acquire(anyString(), anyString(), eq(Instant.parse("2026-10-12T00:00:00Z")),
				eq(Instant.parse("2026-10-12T00:10:00Z")), eq(5))).thenReturn(true);

		service.acquireLogin("203.0.113.4", "MEMBER@EXAMPLE.TEST");

		verify(counters).acquire(eq("LOGIN_IP"), org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"),
				eq(Instant.parse("2026-10-12T00:00:00Z")), eq(Instant.parse("2026-10-12T00:10:00Z")), eq(30));
		verify(counters).acquire(eq("LOGIN_ACCOUNT"), org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"),
				eq(Instant.parse("2026-10-12T00:00:00Z")), eq(Instant.parse("2026-10-12T00:10:00Z")), eq(5));
	}

	@Test
	void blockedAttemptReturnsRemainingWindowRoundedUp() {
		when(counters.acquire(anyString(), anyString(), eq(Instant.parse("2026-10-12T00:00:00Z")),
				eq(Instant.parse("2026-10-12T00:10:00Z")), eq(30))).thenReturn(false);

		assertThatThrownBy(() -> service.acquireLogin("203.0.113.4", "member@example.test"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(exception.retryAfterSeconds()).isEqualTo(599);
				});
	}

	@Test
	void bothBucketsAndTransactionUseOneClockInstantAtWindowBoundary() {
		Clock changingClock = mock(Clock.class);
		Instant beforeBoundary = Instant.parse("2026-10-12T00:09:59.999Z");
		when(changingClock.instant()).thenReturn(beforeBoundary, Instant.parse("2026-10-12T00:10:00Z"));
		AuthAttemptTransaction transaction = mock(AuthAttemptTransaction.class);
		AuthAttemptLimitService changingClockService = new AuthAttemptLimitService(transaction, changingClock, properties);

		changingClockService.acquireLogin("203.0.113.4", "member@example.test");

		verify(changingClock, times(1)).instant();
		verify(transaction).acquire(eq(beforeBoundary), org.mockito.ArgumentMatchers.argThat(buckets ->
				buckets.size() == 2 && buckets.stream().allMatch(bucket ->
						bucket.start().equals(Instant.parse("2026-10-12T00:00:00Z"))
								&& bucket.expiresAt().equals(Instant.parse("2026-10-12T00:10:00Z")))));
	}

	@Test
	void shareBucketsUseSeparateHashedUserAndIpWindows() {
		AuthAttemptTransaction transaction = mock(AuthAttemptTransaction.class);
		AuthAttemptLimitService limits = new AuthAttemptLimitService(transaction, clock, properties);

		limits.acquireShareIssue(42);
		limits.acquireShareRead("203.0.113.4");

		verify(transaction).acquire(eq(clock.instant()), org.mockito.ArgumentMatchers.argThat(buckets ->
				buckets.size() == 1 && buckets.getFirst().type().equals("SHARE_ISSUE_USER")
						&& buckets.getFirst().hash().matches("[0-9a-f]{64}")
						&& buckets.getFirst().limit() == 10
						&& buckets.getFirst().expiresAt().equals(Instant.parse("2026-10-12T01:00:00Z"))));
		verify(transaction).acquire(eq(clock.instant()), org.mockito.ArgumentMatchers.argThat(buckets ->
				buckets.size() == 1 && buckets.getFirst().type().equals("SHARE_READ_IP")
						&& buckets.getFirst().hash().matches("[0-9a-f]{64}")
						&& buckets.getFirst().limit() == 60
						&& buckets.getFirst().expiresAt().equals(Instant.parse("2026-10-12T00:01:00Z"))));
	}
}
