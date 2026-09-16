package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.domain.RequestExecution;
import com.example.travel.user.domain.RequestExecutionStatus;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.repository.RequestExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
class RequestExecutionTransaction {

	private static final Duration LIFETIME = Duration.ofMinutes(10);

	private final RequestExecutionRepository repository;
	private final Clock clock;

	RequestExecutionTransaction(RequestExecutionRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	RequestExecutionLease begin(long userId, UsageFeature feature, UUID requestId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be positive");
		}
		if (feature == null || requestId == null) {
			throw new IllegalArgumentException("feature and requestId are required");
		}

		Instant now = clock.instant();
		Instant expiresAt = now.plus(LIFETIME);
		if (repository.insertProcessingIfAbsent(userId, feature.name(), requestId.toString(), expiresAt) == 1) {
			return new RequestExecutionLease(userId, feature, requestId, expiresAt);
		}

		RequestExecution existing = repository.findByUserIdAndFeatureAndRequestId(userId, feature, requestId)
				.orElseThrow(() -> new IllegalStateException("Request execution disappeared during acquisition"));
		if (!existing.expiresAt().isAfter(now)) {
			existing.restart(expiresAt);
			repository.flush();
			return new RequestExecutionLease(userId, feature, requestId, expiresAt);
		}
		if (existing.status() == RequestExecutionStatus.SUCCESS) {
			throw new ApiException(ErrorCode.REQUEST_ALREADY_COMPLETED);
		}
		throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS);
	}

	@Transactional
	boolean markSucceeded(RequestExecutionLease lease) {
		return repository.markSuccess(
				lease.userId(), lease.feature().name(), lease.requestId().toString(), lease.expiresAt(), clock.instant()) == 1;
	}

	@Transactional
	boolean release(RequestExecutionLease lease) {
		return repository.releaseProcessing(
				lease.userId(), lease.feature().name(), lease.requestId().toString(), lease.expiresAt()) == 1;
	}

	@Transactional
	int deleteExpired(int batchSize) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be positive");
		}
		return repository.deleteExpiredBatch(clock.instant(), batchSize);
	}
}
