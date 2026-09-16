package com.example.travel.user.repository;

import com.example.travel.user.domain.RequestExecution;
import com.example.travel.user.dto.UsageFeature;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RequestExecutionRepository extends JpaRepository<RequestExecution, Long> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT IGNORE INTO request_executions (user_id, feature, request_id, status, expires_at)
			VALUES (:userId, :feature, :requestId, 'PROCESSING', :expiresAt)
			""", nativeQuery = true)
	int insertProcessingIfAbsent(
			@Param("userId") long userId,
			@Param("feature") String feature,
			@Param("requestId") String requestId,
			@Param("expiresAt") Instant expiresAt
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RequestExecution> findByUserIdAndFeatureAndRequestId(
			long userId,
			UsageFeature feature,
			UUID requestId
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE request_executions
			SET status = 'SUCCESS'
			WHERE user_id = :userId
			  AND feature = :feature
			  AND request_id = :requestId
			  AND status = 'PROCESSING'
			  AND expires_at = :leaseExpiresAt
			  AND expires_at > :now
			""", nativeQuery = true)
	int markSuccess(
			@Param("userId") long userId,
			@Param("feature") String feature,
			@Param("requestId") String requestId,
			@Param("leaseExpiresAt") Instant leaseExpiresAt,
			@Param("now") Instant now
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			DELETE FROM request_executions
			WHERE user_id = :userId
			  AND feature = :feature
			  AND request_id = :requestId
			  AND status = 'PROCESSING'
			  AND expires_at = :leaseExpiresAt
			""", nativeQuery = true)
	int releaseProcessing(
			@Param("userId") long userId,
			@Param("feature") String feature,
			@Param("requestId") String requestId,
			@Param("leaseExpiresAt") Instant leaseExpiresAt
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			DELETE FROM request_executions
			WHERE expires_at <= :now
			ORDER BY expires_at
			LIMIT :batchSize
			""", nativeQuery = true)
	int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
