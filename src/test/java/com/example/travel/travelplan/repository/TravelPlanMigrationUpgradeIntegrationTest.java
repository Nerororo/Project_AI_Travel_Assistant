package com.example.travel.travelplan.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TravelPlanMigrationUpgradeIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Test
    void existingV5PlanSurvivesV6AndStayBecomesForbidden() {
        var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("5")).load().migrate();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, created_at, updated_at)
                VALUES (1, 'fixture@example.test', 'encoded', NOW(6), NOW(6))
                """);
        jdbc.update("""
                INSERT INTO travel_plans (id, user_id, title, region_id, region_display_name,
                                          start_date, end_date, travel_mode, meal_travel_buffer_minutes,
                                          created_at, updated_at)
                VALUES (1, 1, 'Existing plan', 'region-1', 'Region snapshot',
                        '2026-10-01', '2026-10-01', 'CAR', 15, NOW(6), NOW(6))
                """);
        jdbc.update("""
                INSERT INTO travel_plan_days (id, travel_plan_id, day_number, travel_date,
                                              activity_start_time, activity_end_time)
                VALUES (1, 1, 1, '2026-10-01', '09:00:00', '18:00:00')
                """);
        jdbc.update("""
                INSERT INTO travel_plan_items (id, travel_plan_day_id, travel_plan_id, item_order,
                                               item_type, start_time, end_time, estimated_minutes)
                VALUES (1, 1, 1, 1, 'MOVE', '09:00:00', '09:10:00', 10)
                """);
        jdbc.update("""
                INSERT INTO plan_places (id, travel_plan_id, kakao_place_id, place_url, role,
                                         display_name, created_at, updated_at)
                VALUES (1, 1, '1', 'https://place.map.kakao.com/1', 'HOTEL',
                        'My hotel', NOW(6), NOW(6))
                """);

        assertThat(jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success = 1", String.class))
                .isEqualTo("5");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM travel_plan_items WHERE item_type = 'STAY'", Integer.class))
                .isZero();

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success = 1", String.class))
                .isEqualTo("6");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM travel_plans WHERE id = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM travel_plan_items WHERE id = 1 AND item_type = 'MOVE'", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO travel_plan_items (travel_plan_day_id, travel_plan_id, item_order,
                                               item_type, plan_place_id, start_time, end_time)
                VALUES (1, 1, 2, 'STAY', 1, '09:10:00', '10:10:00')
                """)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_travel_plan_items_no_stay");
    }
}
