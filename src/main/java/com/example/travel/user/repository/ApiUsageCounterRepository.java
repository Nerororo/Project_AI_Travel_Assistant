package com.example.travel.user.repository;

import com.example.travel.user.domain.ApiUsageCounter;
import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
import com.example.travel.user.dto.UsageFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ApiUsageCounterRepository extends JpaRepository<ApiUsageCounter, Long> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE api_usage_counters
			SET used_count = used_count + :amount
			WHERE scope_type = :scopeType
			  AND scope_id = :scopeId
			  AND feature = :feature
			  AND window_type = :windowType
			  AND window_start = :windowStart
			  AND used_count <= :limitValue - :amount
			""", nativeQuery = true)
	int incrementIfAvailable(
			@Param("scopeType") String scopeType,
			@Param("scopeId") String scopeId,
			@Param("feature") String feature,
			@Param("windowType") String windowType,
			@Param("windowStart") Instant windowStart,
			@Param("amount") long amount,
			@Param("limitValue") long limit
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT IGNORE INTO api_usage_counters
			    (scope_type, scope_id, feature, window_type, window_start, used_count, expires_at)
			VALUES
			    (:scopeType, :scopeId, :feature, :windowType, :windowStart, :amount, :expiresAt)
			""", nativeQuery = true)
	int insertIfAbsent(
			@Param("scopeType") String scopeType,
			@Param("scopeId") String scopeId,
			@Param("feature") String feature,
			@Param("windowType") String windowType,
			@Param("windowStart") Instant windowStart,
			@Param("amount") long amount,
			@Param("expiresAt") Instant expiresAt
	);

	default boolean acquire(UsageScopeType scopeType, String scopeId, UsageFeature feature,
			UsageWindowType windowType, Instant windowStart, Instant expiresAt, long amount, long limit) {
		if (amount > limit) {
			return false;
		}
		String scope = scopeType.name();
		String featureName = feature.name();
		String window = windowType.name();
		if (insertIfAbsent(scope, scopeId, featureName, window, windowStart, amount, expiresAt) == 1) {
			return true;
		}
		return incrementIfAvailable(scope, scopeId, featureName, window, windowStart, amount, limit) == 1;
	}
}
