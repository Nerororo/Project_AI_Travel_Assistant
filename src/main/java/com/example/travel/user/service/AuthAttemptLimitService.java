package com.example.travel.user.service;

import com.example.travel.global.security.JwtProperties;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

@Service
public class AuthAttemptLimitService {
	private static final long TEN_MINUTES = 600;
	private static final long HOUR = 3600;
	private static final int MAX_TRANSACTION_ATTEMPTS = 10;

	private final AuthAttemptTransaction transaction;
	private final Clock clock;
	private final byte[] hmacKey;

	public AuthAttemptLimitService(AuthAttemptTransaction transaction, Clock clock, JwtProperties jwtProperties) {
		this.transaction = transaction;
		this.clock = clock;
		this.hmacKey = jwtProperties.keys().stream()
				.filter(key -> key.id().equals(jwtProperties.activeKeyId()))
				.findFirst()
				.map(key -> Base64.getDecoder().decode(key.secret()))
				.orElseThrow(() -> new IllegalStateException("Active JWT key is required for auth limits"));
	}

	public void acquireRegistration(String clientIp, String email) {
		Instant now = clock.instant();
		acquire(now, List.of(bucket("REGISTER_IP", clientIp, HOUR, 5, now),
				bucket("REGISTER_ACCOUNT", email.toLowerCase(Locale.ROOT), HOUR, 3, now)));
	}

	public void acquireLogin(String clientIp, String email) {
		Instant now = clock.instant();
		acquire(now, List.of(bucket("LOGIN_IP", clientIp, TEN_MINUTES, 30, now),
				bucket("LOGIN_ACCOUNT", email.toLowerCase(Locale.ROOT), TEN_MINUTES, 5, now)));
	}

	public void acquireShareIssue(long userId) {
		Instant now = clock.instant();
		acquire(now, List.of(bucket("SHARE_ISSUE_USER", Long.toString(userId), HOUR, 10, now)));
	}

	public void acquireShareRead(String clientIp) {
		Instant now = clock.instant();
		acquire(now, List.of(bucket("SHARE_READ_IP", clientIp, 60, 60, now)));
	}

	private void acquire(Instant now, List<AuthAttemptTransaction.Bucket> buckets) {
		for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
			try {
				transaction.acquire(now, buckets);
				return;
			} catch (CannotAcquireLockException exception) {
				if (attempt == MAX_TRANSACTION_ATTEMPTS) {
					throw exception;
				}
				long backoffMillis = ThreadLocalRandom.current().nextLong(5L, 5L + attempt * 10L);
				LockSupport.parkNanos(backoffMillis * 1_000_000L);
			}
		}
	}

	private AuthAttemptTransaction.Bucket bucket(String bucket, String identifier, long windowSeconds, int limit,
			Instant now) {
		Instant start = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds);
		Instant expiresAt = start.plusSeconds(windowSeconds);
		return new AuthAttemptTransaction.Bucket(bucket, hmac(bucket + ":" + identifier), start, expiresAt, limit);
	}

	private String hmac(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Auth attempt hashing failed", exception);
		}
	}
}
