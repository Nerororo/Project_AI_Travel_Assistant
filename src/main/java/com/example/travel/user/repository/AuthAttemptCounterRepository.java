package com.example.travel.user.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class AuthAttemptCounterRepository {

	private final JdbcTemplate jdbc;

	public AuthAttemptCounterRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void deleteExpired(Instant now) {
		jdbc.update("DELETE FROM auth_attempt_counters WHERE expires_at < ? LIMIT 100", Timestamp.from(now));
	}

	public boolean acquire(String bucketType, String identifierHash, Instant windowStart, Instant expiresAt,
			int limit) {
		jdbc.update("""
				INSERT IGNORE INTO auth_attempt_counters
				(bucket_type, identifier_hash, window_start, used_count, expires_at)
				VALUES (?, ?, ?, 0, ?)
				""", bucketType, identifierHash, Timestamp.from(windowStart), Timestamp.from(expiresAt));
		return jdbc.update("""
				UPDATE auth_attempt_counters SET used_count = used_count + 1
				WHERE bucket_type = ? AND identifier_hash = ? AND window_start = ? AND used_count < ?
				""", bucketType, identifierHash, Timestamp.from(windowStart), limit) == 1;
	}
}
