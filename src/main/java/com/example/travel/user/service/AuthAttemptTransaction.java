package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.repository.AuthAttemptCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
class AuthAttemptTransaction {
	private final AuthAttemptCounterRepository counters;

	AuthAttemptTransaction(AuthAttemptCounterRepository counters) {
		this.counters = counters;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void acquire(Instant now, List<Bucket> buckets) {
		counters.deleteExpired(now);
		for (Bucket bucket : buckets) {
			if (!counters.acquire(bucket.type(), bucket.hash(), bucket.start(), bucket.expiresAt(), bucket.limit())) {
				long retryAfter = Math.max(1, bucket.expiresAt().getEpochSecond() - now.getEpochSecond());
				throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), retryAfter);
			}
		}
	}

	record Bucket(String type, String hash, Instant start, Instant expiresAt, int limit) {
	}
}
