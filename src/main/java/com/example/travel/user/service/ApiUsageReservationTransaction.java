package com.example.travel.user.service;

import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.repository.ApiUsageCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
class ApiUsageReservationTransaction {

	private final ApiUsageCounterRepository counterRepository;
	private final ApiUsagePolicy usagePolicy;

	ApiUsageReservationTransaction(ApiUsageCounterRepository counterRepository, ApiUsagePolicy usagePolicy) {
		this.counterRepository = counterRepository;
		this.usagePolicy = usagePolicy;
	}

	@Transactional
	void acquire(long userId, UsageFeature feature, long amount) {
		List<Long> retryAfterCandidates = new ArrayList<>();
		for (ApiUsagePolicy.UsageWindow window : usagePolicy.windows(userId, feature)) {
			boolean acquired = counterRepository.acquire(
					window.scopeType(), window.scopeId(), feature, window.windowType(),
					window.windowStart(), window.expiresAt(), amount, window.limit());
			if (!acquired) {
				retryAfterCandidates.add(usagePolicy.retryAfterSeconds(window));
			}
		}
		if (!retryAfterCandidates.isEmpty()) {
			throw new UsageLimitExceeded(retryAfterCandidates.stream().mapToLong(Long::longValue).max().orElseThrow());
		}
	}

	static final class UsageLimitExceeded extends RuntimeException {
		private final long retryAfterSeconds;

		UsageLimitExceeded(long retryAfterSeconds) {
			this.retryAfterSeconds = retryAfterSeconds;
		}

		long retryAfterSeconds() {
			return retryAfterSeconds;
		}
	}
}
