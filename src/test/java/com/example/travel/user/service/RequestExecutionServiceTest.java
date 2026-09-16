package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageReservationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequestExecutionServiceTest {

	private final RequestExecutionTransaction executionTransaction = mock(RequestExecutionTransaction.class);
	private final ApiUsageService usageService = mock(ApiUsageService.class);
	private final RequestExecutionService service = new RequestExecutionService(executionTransaction, usageService);

	@Test
	void duplicateIsRejectedBeforeUsageIsAcquired() {
		UUID requestId = UUID.randomUUID();
		when(executionTransaction.begin(1L, UsageFeature.PLACE_SEARCH, requestId))
				.thenThrow(new ApiException(ErrorCode.REQUEST_IN_PROGRESS));

		assertThatThrownBy(() -> service.tryStart(1L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
		verifyNoInteractions(usageService);
	}

	@Test
	void rateLimitDenialReleasesProcessingLease() {
		RequestExecutionLease lease = lease();
		when(executionTransaction.begin(lease.userId(), lease.feature(), lease.requestId())).thenReturn(lease);
		when(usageService.tryAcquire(lease.userId(), lease.feature(), 3))
				.thenReturn(UsageReservationResult.denied(25));

		var result = service.tryStart(lease.userId(), lease.feature(), lease.requestId(), 3);

		assertThat(result.started()).isFalse();
		assertThat(result.retryAfterSeconds()).isEqualTo(25L);
		verify(executionTransaction).release(lease);
	}

	@Test
	void successfulStartKeepsLeaseUntilCallerCompletesIt() {
		RequestExecutionLease lease = lease();
		when(executionTransaction.begin(lease.userId(), lease.feature(), lease.requestId())).thenReturn(lease);
		when(usageService.tryAcquire(lease.userId(), lease.feature(), 1))
				.thenReturn(UsageReservationResult.success());

		var result = service.tryStart(lease.userId(), lease.feature(), lease.requestId(), 1);

		assertThat(result.lease()).isEqualTo(lease);
		verify(executionTransaction, never()).release(lease);
	}

	@Test
	void preparationFailureReleasesLeaseWithoutHidingOriginalFailure() {
		RequestExecutionLease lease = lease();
		IllegalStateException failure = new IllegalStateException("safe-test-failure");
		when(executionTransaction.begin(lease.userId(), lease.feature(), lease.requestId())).thenReturn(lease);
		when(usageService.tryAcquire(lease.userId(), lease.feature(), 1)).thenThrow(failure);

		assertThatThrownBy(() -> service.tryStart(lease.userId(), lease.feature(), lease.requestId(), 1))
				.isSameAs(failure);
		verify(executionTransaction).release(lease);
	}

	private static RequestExecutionLease lease() {
		return new RequestExecutionLease(
				1L,
				UsageFeature.PLACE_SEARCH,
				UUID.randomUUID(),
				Instant.parse("2026-09-16T00:10:00Z"));
	}
}
