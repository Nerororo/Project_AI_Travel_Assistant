package com.example.travel.user.repository;

import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiUsageCounterRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	private ApiUsageCounterRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void atomicallyAcquiresOnlyUpToLimit() {
		Instant start = Instant.parse("2026-09-16T00:00:00Z");
		Instant expires = start.plusSeconds(60);

		assertThat(repository.acquire(UsageScopeType.USER, "11", UsageFeature.PLACE_SEARCH,
				UsageWindowType.MINUTE, start, expires, 19, 20)).isTrue();
		assertThat(repository.acquire(UsageScopeType.USER, "11", UsageFeature.PLACE_SEARCH,
				UsageWindowType.MINUTE, start, expires, 1, 20)).isTrue();
		assertThat(repository.acquire(UsageScopeType.USER, "11", UsageFeature.PLACE_SEARCH,
				UsageWindowType.MINUTE, start, expires, 1, 20)).isFalse();

		assertThat(repository.findAll()).singleElement()
				.extracting(counter -> counter.usedCount())
				.isEqualTo(20L);
	}

	@Test
	void migrationContainsOnlyCounterContractColumns() {
		List<String> columns = jdbcTemplate.queryForList("""
				SELECT column_name
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = 'api_usage_counters'
				ORDER BY ordinal_position
				""", String.class);

		assertThat(columns).containsExactly(
				"id", "scope_type", "scope_id", "feature", "window_type",
				"window_start", "used_count", "expires_at");
		assertThat(columns).noneMatch(column -> column.contains("payload")
				|| column.contains("response") || column.contains("coordinate") || column.contains("resource"));
	}
}
