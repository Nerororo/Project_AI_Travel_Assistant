package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
import com.example.travel.user.dto.UsageFeature;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ApiUsageServiceTest {

	@Test
	void policyCreatesUserMinuteDayAndKnownServiceDayWindows() {
		Instant now = Instant.parse("2026-09-16T14:59:30Z");
		ApiUsagePolicy policy = new ApiUsagePolicy(Clock.fixed(now, ZoneOffset.UTC));

		var windows = policy.windows(42L, UsageFeature.PUBLIC_TRANSIT_ROUTE);

		assertThat(windows).hasSize(3);
		assertThat(windows.get(0))
				.extracting(ApiUsagePolicy.UsageWindow::scopeType, ApiUsagePolicy.UsageWindow::windowType,
						ApiUsagePolicy.UsageWindow::windowStart, ApiUsagePolicy.UsageWindow::expiresAt,
						ApiUsagePolicy.UsageWindow::limit)
				.containsExactly(UsageScopeType.USER, UsageWindowType.MINUTE,
						Instant.parse("2026-09-16T14:59:00Z"), Instant.parse("2026-09-16T15:00:00Z"), 60L);
		assertThat(windows.get(1).windowStart()).isEqualTo(Instant.parse("2026-09-15T15:00:00Z"));
		assertThat(windows.get(1).expiresAt()).isEqualTo(Instant.parse("2026-09-16T15:00:00Z"));
		assertThat(windows.get(2).scopeType()).isEqualTo(UsageScopeType.SERVICE);
		assertThat(windows.get(2).limit()).isEqualTo(900L);
		assertThat(policy.retryAfterSeconds(windows.get(1))).isEqualTo(30L);
	}

	@Test
	void policyKeepsAiServiceBudgetUnconfiguredWhileApplyingExactUserLimits() {
		ApiUsagePolicy policy = new ApiUsagePolicy(
				Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC));

		var regionWindows = policy.windows(1L, UsageFeature.AI_REGION_RECOMMENDATION);
		var menuWindows = policy.windows(1L, UsageFeature.AI_MENU_ANALYSIS);

		assertThat(regionWindows).hasSize(2);
		assertThat(regionWindows).extracting(ApiUsagePolicy.UsageWindow::limit)
				.containsExactly(2L, 10L);
		assertThat(menuWindows).hasSize(2);
		assertThat(menuWindows).extracting(ApiUsagePolicy.UsageWindow::limit)
				.containsExactly(3L, 15L);
	}

	@Test
	void returnsDenialAndMapsItToCommonRateLimitException() {
		ApiUsageReservationTransaction transaction = mock(ApiUsageReservationTransaction.class);
		doThrow(new ApiUsageReservationTransaction.UsageLimitExceeded(37L))
				.when(transaction).acquire(5L, UsageFeature.PLACE_SEARCH, 1L);
		ApiUsageService service = new ApiUsageService(transaction);

		var result = service.tryAcquire(5L, UsageFeature.PLACE_SEARCH, 1L);

		assertThat(result.acquired()).isFalse();
		assertThat(result.retryAfterSeconds()).isEqualTo(37L);
		assertThatThrownBy(() -> service.acquireOrThrow(5L, UsageFeature.PLACE_SEARCH, 1L))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(exception.retryAfterSeconds()).isEqualTo(37L);
				});
	}

	@Test
	void rejectsNonPositiveReservationAmount() {
		ApiUsageService service = new ApiUsageService(mock(ApiUsageReservationTransaction.class));

		assertThatThrownBy(() -> service.tryAcquire(1L, UsageFeature.CAR_ROUTE, 0L))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
