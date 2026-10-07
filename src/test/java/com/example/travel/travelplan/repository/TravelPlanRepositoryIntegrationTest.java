package com.example.travel.travelplan.repository;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.FoodPreference;
import com.example.travel.travelplan.domain.PlanPlace;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.domain.TravelPlanDay;
import com.example.travel.travelplan.domain.TravelPlanItem;
import com.example.travel.travelplan.domain.TravelPlanShare;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TravelPlanRepositoryIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired UserRepository users;
    @Autowired TravelPlanRepository plans;
    @Autowired FoodPreferenceRepository foods;
    @Autowired TravelPlanShareRepository shares;
    @Autowired PlanPlaceRepository places;
    @Autowired TravelPlanDayRepository days;
    @Autowired TravelPlanItemRepository items;
    @Autowired JdbcTemplate jdbc;

    private TravelPlan plan(String email) {
        Long userId = users.saveAndFlush(new User(email, "encoded")).id();
        return plans.saveAndFlush(new TravelPlan(userId, "My trip", "region-1", "Region snapshot",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), TravelMode.CAR, 15));
    }

    @Test
    void completedPlanTablesHaveNoForbiddenProviderOrRequestColumns() {
        List<String> columns = jdbc.queryForList("""
                SELECT LOWER(column_name)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN ('travel_plans', 'food_preferences', 'travel_plan_shares',
                                     'plan_places', 'travel_plan_days', 'travel_plan_items')
                """, String.class);

        assertThat(columns).isNotEmpty().noneMatch(column -> column.matches(
                ".*(coordinate|latitude|longitude|address|phone|category|provider|raw|payload|response|"
                        + "polyline|warning|selection_token|search_query|prompt).*"));
    }

    @Test
    void flywayCreatesSchemaAndJpaRestoresAggregateRows() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = 1", Integer.class)).isEqualTo(1);
        TravelPlan plan = plan("plan-one@example.test");
        FoodPreference food = foods.saveAndFlush(new FoodPreference(plan.id(), "noodles"));
        TravelPlanShare share = shares.saveAndFlush(new TravelPlanShare(plan.id(), new byte[32],
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z")));
        PlanPlace place = places.saveAndFlush(new PlanPlace(plan.id(), "kakao-1", "https://place.map.kakao.com/1",
                PlanPlace.Role.ATTRACTION, "My museum", "My note", 60));
        TravelPlanDay day = days.saveAndFlush(new TravelPlanDay(plan.id(), 1, LocalDate.of(2026, 10, 1),
                LocalTime.of(9, 0), LocalTime.of(18, 0)));
        TravelPlanItem visit = items.saveAndFlush(new TravelPlanItem(day.id(), plan.id(), 1,
                TravelPlanItem.Type.VISIT, place.id(), LocalTime.of(9, 0), LocalTime.of(10, 0), null));
        TravelPlanItem meal = items.saveAndFlush(new TravelPlanItem(day.id(), plan.id(), 2,
                TravelPlanItem.Type.MEAL, null, LocalTime.of(12, 0), LocalTime.of(13, 0), null));

        assertThat(plans.findByIdAndUserId(plan.id(), plan.userId())).get()
                .extracting(TravelPlan::regionDisplayName).isEqualTo("Region snapshot");
        assertThat(foods.findByTravelPlanId(plan.id())).extracting(FoodPreference::foodName).containsExactly("noodles");
        assertThat(shares.findByTokenHash(new byte[32])).get().extracting(TravelPlanShare::travelPlanId).isEqualTo(plan.id());
        assertThat(places.findByTravelPlanId(plan.id())).extracting(PlanPlace::displayName).containsExactly("My museum");
        assertThat(days.findByTravelPlanIdOrderByDayNumber(plan.id())).extracting(TravelPlanDay::travelDate)
                .containsExactly(LocalDate.of(2026, 10, 1));
        assertThat(items.findByTravelPlanDayIdOrderByItemOrder(day.id())).extracting(TravelPlanItem::id)
                .containsExactly(visit.id(), meal.id());
        assertThat(food.id()).isPositive();
        assertThat(share.tokenHash()).hasSize(32);
    }

    @Test
    void databaseRejectsWrongPlanForeignKeyAndInvalidItemShape() {
        TravelPlan first = plan("plan-first@example.test");
        TravelPlan second = plan("plan-second@example.test");
        PlanPlace otherPlace = places.saveAndFlush(new PlanPlace(second.id(), "kakao-2", "https://place.map.kakao.com/2",
                PlanPlace.Role.ATTRACTION, "My museum", null, 60));
        TravelPlanDay day = days.saveAndFlush(new TravelPlanDay(first.id(), 1, LocalDate.of(2026, 10, 1),
                LocalTime.of(9, 0), LocalTime.of(18, 0)));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO travel_plan_items
                  (travel_plan_day_id, travel_plan_id, item_order, item_type, plan_place_id, start_time, end_time)
                VALUES (?, ?, 1, 'VISIT', ?, '09:00:00', '10:00:00')
                """, day.id(), first.id(), otherPlace.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO travel_plan_items
                  (travel_plan_day_id, travel_plan_id, item_order, item_type, start_time, end_time, estimated_minutes)
                VALUES (?, ?, 1, 'MOVE', '09:00:00', '10:00:00', 15)
                """, day.id(), first.id())).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_travel_plan_items_shape");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO travel_plan_items
                  (travel_plan_day_id, travel_plan_id, item_order, item_type, start_time, end_time)
                VALUES (?, ?, 1, 'MOVE', '09:00:00', '10:00:00')
                """, day.id(), first.id())).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_travel_plan_items_shape");
        assertThatThrownBy(() -> places.saveAndFlush(new PlanPlace(first.id(), "kakao-3",
                "https://place.map.kakao.com/3", PlanPlace.Role.ATTRACTION, "My park", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsStayItemForOwnHotel() {
        TravelPlan plan = plan("plan-stay@example.test");
        PlanPlace hotel = places.saveAndFlush(new PlanPlace(plan.id(), "kakao-hotel",
                "https://place.map.kakao.com/hotel", PlanPlace.Role.HOTEL, "My hotel", null, null));
        TravelPlanDay day = days.saveAndFlush(new TravelPlanDay(plan.id(), 1, LocalDate.of(2026, 10, 1),
                LocalTime.of(9, 0), LocalTime.of(18, 0)));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO travel_plan_items
                  (travel_plan_day_id, travel_plan_id, item_order, item_type, plan_place_id, start_time, end_time)
                VALUES (?, ?, 1, 'STAY', ?, '09:00:00', '10:00:00')
                """, day.id(), plan.id(), hotel.id())).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_travel_plan_items_no_stay");
    }

    @Test
    void databaseRejectsDuplicateFoodNameWithinOnePlan() {
        TravelPlan plan = plan("duplicate-food@example.test");
        foods.saveAndFlush(new FoodPreference(plan.id(), "noodles"));

        assertThatThrownBy(() -> foods.saveAndFlush(new FoodPreference(plan.id(), "noodles")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
