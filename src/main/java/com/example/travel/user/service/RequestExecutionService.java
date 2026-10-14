package com.example.travel.user.service;

import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageReservationResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

@Service
public class RequestExecutionService {

	private static final int MAX_TRANSACTION_ATTEMPTS = 10;
	private static final Logger log = LoggerFactory.getLogger(RequestExecutionService.class);

	private final RequestExecutionTransaction executionTransaction;
	private final ApiUsageService usageService;
	private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "request-execution-renewal");
		thread.setDaemon(true);
		return thread;
	});

	public RequestExecutionService(RequestExecutionTransaction executionTransaction, ApiUsageService usageService) {
		this.executionTransaction = executionTransaction;
		this.usageService = usageService;
	}

	public RequestStartResult tryStart(long userId, UsageFeature feature, UUID requestId, long usageAmount) {
		RequestExecutionLease lease = beginWithDeadlockRetry(userId, feature, requestId);
		try {
			UsageReservationResult usage = usageService.tryAcquire(userId, feature, usageAmount);
			if (!usage.acquired()) {
				executionTransaction.release(lease);
				return RequestStartResult.rateLimited(usage.retryAfterSeconds());
			}
			lease.scheduleRenewal(renewals.scheduleAtFixedRate(() -> renew(lease), 2, 2, TimeUnit.MINUTES));
			return RequestStartResult.started(lease);
		} catch (RuntimeException exception) {
			executionTransaction.release(lease);
			throw exception;
		}
	}

	public boolean markSucceeded(RequestExecutionLease lease) {
		synchronized (lease) {
			lease.stopRenewal();
			return executionTransaction.markSucceeded(lease);
		}
	}

	public boolean releaseAfterFailure(RequestExecutionLease lease) {
		synchronized (lease) {
			lease.stopRenewal();
			return executionTransaction.release(lease);
		}
	}

	void renew(RequestExecutionLease lease) {
		synchronized (lease) {
			try {
				Instant expiresAt = executionTransaction.renewProcessing(lease);
				if (expiresAt == null) {
					lease.stopRenewal();
				} else {
					lease.renewTo(expiresAt);
				}
			} catch (RuntimeException exception) {
				log.warn("Request execution renewal failed; retrying at the next interval", exception);
			}
		}
	}

	@PreDestroy
	void stopRenewals() {
		renewals.shutdownNow();
	}

	public int deleteExpired(int batchSize) {
		return executionTransaction.deleteExpired(batchSize);
	}

	private RequestExecutionLease beginWithDeadlockRetry(long userId, UsageFeature feature, UUID requestId) {
		for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
			try {
				return executionTransaction.begin(userId, feature, requestId);
			} catch (CannotAcquireLockException exception) {
				if (attempt == MAX_TRANSACTION_ATTEMPTS) {
					throw exception;
				}
				long backoffMillis = ThreadLocalRandom.current().nextLong(5L, 5L + attempt * 10L);
				LockSupport.parkNanos(backoffMillis * 1_000_000L);
			}
		}
		throw new IllegalStateException("Unreachable transaction attempt state");
	}
}
