package com.example.travel.user.repository;

import com.example.travel.user.domain.RequestExecutionStatus;
import com.example.travel.user.domain.User;
import com.example.travel.user.dto.UsageFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RequestExecutionRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	private RequestExecutionRepository repository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void insertsAndFindsOnlyOneExecutionForSameKey() {
		User user = userRepository.saveAndFlush(new User("request-owner@example.test", "stored-hash"));
		UUID requestId = UUID.randomUUID();
		Instant expiresAt = Instant.parse("2026-09-16T00:10:00Z");

		assertThat(repository.insertProcessingIfAbsent(
				user.id(), UsageFeature.PLACE_SEARCH.name(), requestId.toString(), expiresAt)).isEqualTo(1);
		assertThat(repository.insertProcessingIfAbsent(
				user.id(), UsageFeature.PLACE_SEARCH.name(), requestId.toString(), expiresAt)).isZero();

		assertThat(repository.findByUserIdAndFeatureAndRequestId(
				user.id(), UsageFeature.PLACE_SEARCH, requestId))
				.get()
				.extracting(execution -> execution.status(), execution -> execution.expiresAt())
				.containsExactly(RequestExecutionStatus.PROCESSING, expiresAt);
	}

	@Test
	void userDeletionCascadesToRequestExecutions() {
		User user = userRepository.saveAndFlush(new User("deleted-owner@example.test", "stored-hash"));
		repository.insertProcessingIfAbsent(user.id(), UsageFeature.CAR_ROUTE.name(),
				UUID.randomUUID().toString(), Instant.parse("2026-09-16T00:10:00Z"));

		userRepository.deleteById(user.id());
		userRepository.flush();

		assertThat(repository.count()).isZero();
	}

	@Test
	void migrationStoresNoResultOrPayloadColumns() {
		List<String> columns = jdbcTemplate.queryForList("""
				SELECT column_name
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = 'request_executions'
				ORDER BY ordinal_position
				""", String.class);

		assertThat(columns).containsExactly("id", "user_id", "feature", "request_id", "status", "expires_at");
		assertThat(columns).noneMatch(column -> column.contains("payload") || column.contains("response")
				|| column.contains("result") || column.contains("coordinate") || column.contains("resource"));
	}
}
