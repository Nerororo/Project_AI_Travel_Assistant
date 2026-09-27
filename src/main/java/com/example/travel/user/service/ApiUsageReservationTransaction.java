package com.example.travel.user.service;

import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageDenialScope;
import com.example.travel.user.repository.ApiUsageCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
class ApiUsageReservationTransaction {

	private final ApiUsageCounterRepository counterRepository;
	private final ApiUsagePolicy usagePolicy;

	ApiUsageReservationTransaction(ApiUsageCounterRepository counterRepository, ApiUsagePolicy usagePolicy) {
		this.counterRepository = counterRepository;
		this.usagePolicy = usagePolicy;
	}

	@Transactional
	Instant acquire(long userId, UsageFeature feature, long amount) {
		List<Long> retryAfterCandidates = new ArrayList<>();
		EnumSet<UsageDenialScope> deniedScopes = EnumSet.noneOf(UsageDenialScope.class);
		List<ApiUsagePolicy.UsageWindow> windows = usagePolicy.windows(userId, feature);
		for (ApiUsagePolicy.UsageWindow window : windows) {
			boolean acquired = counterRepository.acquire(
					window.scopeType(), window.scopeId(), feature, window.windowType(),
					window.windowStart(), window.expiresAt(), amount, window.limit());
			if (!acquired) {
				retryAfterCandidates.add(usagePolicy.retryAfterSeconds(window));
				deniedScopes.add(UsageDenialScope.valueOf(window.scopeType().name()));
			}
		}
		if (!retryAfterCandidates.isEmpty()) {
			throw new UsageLimitExceeded(
					retryAfterCandidates.stream().mapToLong(Long::longValue).max().orElseThrow(),
					deniedScopes
			);
		}
		return windows.getFirst().windowStart();
	}

	@Transactional
	void release(long userId, UsageFeature feature, Instant windowReference, long amount) {
		for (ApiUsagePolicy.UsageWindow window : usagePolicy.windows(userId, feature, windowReference)) {
			boolean released = counterRepository.release(
					window.scopeType(),
					window.scopeId(),
					feature,
					window.windowType(),
					window.windowStart(),
					amount
			);
			if (!released) {
				throw new IllegalStateException("Reserved usage could not be released");
			}
		}
	}

	static final class UsageLimitExceeded extends RuntimeException {
		private final long retryAfterSeconds;
		private final Set<UsageDenialScope> deniedScopes;

		UsageLimitExceeded(long retryAfterSeconds, Set<UsageDenialScope> deniedScopes) {
			this.retryAfterSeconds = retryAfterSeconds;
			this.deniedScopes = Set.copyOf(deniedScopes);
		}

		long retryAfterSeconds() {
			return retryAfterSeconds;
		}

		Set<UsageDenialScope> deniedScopes() {
			return deniedScopes;
		}
	}
}
