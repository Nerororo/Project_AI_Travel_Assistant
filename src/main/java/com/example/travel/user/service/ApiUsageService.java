package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageReservationResult;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

@Service
public class ApiUsageService {
	private static final int MAX_TRANSACTION_ATTEMPTS = 10;

	private final ApiUsageReservationTransaction reservationTransaction;

	public ApiUsageService(ApiUsageReservationTransaction reservationTransaction) {
		this.reservationTransaction = reservationTransaction;
	}

	public UsageReservationResult tryAcquire(long userId, UsageFeature feature, long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
			try {
				reservationTransaction.acquire(userId, feature, amount);
				return UsageReservationResult.success();
			} catch (ApiUsageReservationTransaction.UsageLimitExceeded exception) {
				return UsageReservationResult.denied(exception.retryAfterSeconds());
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

	public void acquireOrThrow(long userId, UsageFeature feature, long amount) {
		UsageReservationResult result = tryAcquire(userId, feature, amount);
		if (!result.acquired()) {
			throw new ApiException(
					ErrorCode.RATE_LIMIT_EXCEEDED,
					null,
					List.of(),
					result.retryAfterSeconds()
			);
		}
	}
}
