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
}
