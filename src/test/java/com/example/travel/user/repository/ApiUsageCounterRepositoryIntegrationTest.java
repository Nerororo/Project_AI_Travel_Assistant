package com.example.travel.user.repository;

import com.example.travel.user.domain.ApiUsageCounter;
import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
import com.example.travel.user.dto.UsageFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
	void atomicallyAcquiresCarRouteMinuteAndDayWindowsOnlyUpToTheirLimits() {
		Instant minuteStart = Instant.parse("2026-09-16T00:00:00Z");
		Instant dayStart = Instant.parse("2026-09-15T15:00:00Z");

		assertThat(repository.acquire(UsageScopeType.USER, "21", UsageFeature.CAR_ROUTE,
				UsageWindowType.MINUTE, minuteStart, minuteStart.plusSeconds(60), 60, 60)).isTrue();
		assertThat(repository.acquire(UsageScopeType.USER, "21", UsageFeature.CAR_ROUTE,
				UsageWindowType.MINUTE, minuteStart, minuteStart.plusSeconds(60), 1, 60)).isFalse();
		assertThat(repository.acquire(UsageScopeType.USER, "21", UsageFeature.CAR_ROUTE,
				UsageWindowType.DAY, dayStart, dayStart.plusSeconds(86_400), 120, 120)).isTrue();
		assertThat(repository.acquire(UsageScopeType.USER, "21", UsageFeature.CAR_ROUTE,
				UsageWindowType.DAY, dayStart, dayStart.plusSeconds(86_400), 1, 120)).isFalse();

		assertThat(repository.findAll())
				.filteredOn(counter -> counter.feature() == UsageFeature.CAR_ROUTE)
				.extracting(ApiUsageCounter::windowType, ApiUsageCounter::usedCount)
				.containsExactlyInAnyOrder(
						tuple(UsageWindowType.MINUTE, 60L),
						tuple(UsageWindowType.DAY, 120L));
	}

	@Test
	void migrationRejectsUnknownUsageFeature() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO api_usage_counters
				    (scope_type, scope_id, feature, window_type, window_start, used_count, expires_at)
				VALUES
				    ('USER', '31', 'UNKNOWN_ROUTE', 'MINUTE',
				     '2026-09-16 00:00:00.000000', 1, '2026-09-16 00:01:00.000000')
				"""))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ck_api_usage_counters_feature");
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
