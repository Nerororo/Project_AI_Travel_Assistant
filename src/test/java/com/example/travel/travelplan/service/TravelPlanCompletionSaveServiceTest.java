package com.example.travel.travelplan.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.dto.CompletedPlanSaveCommand;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.TravelConditions;
import com.example.travel.travelplan.repository.FoodPreferenceRepository;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class TravelPlanCompletionSaveServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final UUID ATTRACTION = new UUID(0, 1);
    private static final String SECRET = randomSecret();

    @Container @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("routy.jwt.active-key-id", () -> "test-active");
        registry.add("routy.jwt.keys[0].id", () -> "test-active");
        registry.add("routy.jwt.keys[0].secret", () -> SECRET);
    }

    @Autowired TravelPlanCompletionSaveService service;
    @Autowired UserRepository users;
    @Autowired TravelPlanRepository plans;
    @Autowired TravelPlanDayRepository days;
    @Autowired TravelPlanItemRepository items;
    @Autowired PlanPlaceRepository places;
    @Autowired FoodPreferenceRepository foods;

    @Test
    void storesWholeCalculatedPlanWithoutWarningsCoordinatesOrZeroMinuteMoves() {
        long userId = user();
        long planId = service.save(command(userId, false));

        assertThat(plans.findById(planId)).get().satisfies(plan -> {
            assertThat(plan.userId()).isEqualTo(userId);
            assertThat(plan.title()).isEqualTo("My trip");
            assertThat(plan.regionDisplayName()).isEqualTo("Region snapshot");
        });
        assertThat(foods.findByTravelPlanId(planId)).extracting(food -> food.foodName())
                .containsExactly("noodles");
        assertThat(places.findByTravelPlanId(planId)).hasSize(2)
                .extracting(place -> place.displayName()).containsExactlyInAnyOrder("My museum", "My lunch");
        var day = days.findByTravelPlanIdOrderByDayNumber(planId).getFirst();
        assertThat(day.travelDate()).isEqualTo(DATE);
        var savedItems = items.findByTravelPlanDayIdOrderByItemOrder(day.id());
        assertThat(savedItems).extracting(item -> item.itemOrder()).containsExactly(1, 2, 3, 4);
        assertThat(savedItems).extracting(item -> item.itemType().name())
                .containsExactly("VISIT", "MOVE", "MEAL", "MOVE");
        assertThat(savedItems.get(2).planPlaceId()).isNotNull();
        assertThat(savedItems.get(1).estimatedMinutes()).isEqualTo(10);
    }

    @Test
    void hotelUsesGenericDefaultOrTheUsersChosenName() {
        long userId = user();

        long defaultPlan = service.save(hotelCommand(userId, null));
        long renamedPlan = service.save(hotelCommand(userId, "우리 숙소"));

        assertThat(places.findByTravelPlanId(defaultPlan)).singleElement()
                .extracting(place -> place.displayName()).isEqualTo("숙소");
        assertThat(places.findByTravelPlanId(renamedPlan)).singleElement()
                .extracting(place -> place.displayName()).isEqualTo("우리 숙소");
    }

    @Test
    void itemInsertFailureRollsBackPlanAndEveryChild() {
        long userId = user();
        long dayCount = days.count();
        long itemCount = items.count();
        long placeCount = places.count();
        long foodCount = foods.count();

        assertThatThrownBy(() -> service.save(command(userId, true)))
                .isInstanceOf(DataAccessException.class);
        assertThat(plans.findByUserId(userId)).isEmpty();
        assertThat(days.count()).isEqualTo(dayCount);
        assertThat(items.count()).isEqualTo(itemCount);
        assertThat(places.count()).isEqualTo(placeCount);
        assertThat(foods.count()).isEqualTo(foodCount);
    }

    private long user() {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "encoded")).id();
    }

    private static CompletedPlanSaveCommand command(long userId, boolean invalidVisitTime) {
        var window = new DailyActivityWindow(DATE, LocalTime.of(9, 0), LocalTime.of(15, 0));
        var conditions = TravelConditions.withDefaultMealTravelBuffer(
                new TravelPeriod(DATE, DATE), TravelMode.CAR, List.of(window));
        var attraction = new CompletedPlanSaveCommand.Place("1", "https://place.map.kakao.com/1",
                "My museum", "My note", 60);
        var restaurant = new CompletedPlanSaveCommand.Place("2", "https://place.map.kakao.com/2",
                "My lunch", null, null);
        var calculated = new EstimatedDay(DATE, DATE.atTime(13, 10), List.of(
                item(1, EstimatedItem.Type.MOVE, null, null, 9, 0, 9, 0, 0),
                item(2, EstimatedItem.Type.VISIT, ATTRACTION, null, 9, 0,
                        invalidVisitTime ? 9 : 10, 0, null),
                item(3, EstimatedItem.Type.MOVE, null, null, 11, 50, 12, 0, 10),
                item(4, EstimatedItem.Type.MEAL, null, MealType.LUNCH, 12, 0, 13, 0, null),
                item(5, EstimatedItem.Type.MOVE, null, null, 13, 0, 13, 10, 10)));
        return new CompletedPlanSaveCommand(userId, " My trip ", "region-1", "Region snapshot",
                conditions, List.of("noodles"), Map.of(ATTRACTION, attraction), null,
                Map.of(new CompletedPlanSaveCommand.MealKey(DATE, MealType.LUNCH), restaurant),
                List.of(calculated));
    }

    private static CompletedPlanSaveCommand hotelCommand(long userId, String displayName) {
        LocalDate next = DATE.plusDays(1);
        var conditions = TravelConditions.withDefaultMealTravelBuffer(
                new TravelPeriod(DATE, next), TravelMode.CAR, List.of(
                        new DailyActivityWindow(DATE, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        new DailyActivityWindow(next, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        var hotel = new CompletedPlanSaveCommand.Place("3", "https://place.map.kakao.com/3",
                displayName, null, null);
        return new CompletedPlanSaveCommand(userId, "My trip", "region-1", "Region snapshot",
                conditions, List.of("noodles"), Map.of(), hotel, Map.of(), List.of(
                        new EstimatedDay(DATE, DATE.atTime(9, 10), List.of(new EstimatedItem(1,
                                EstimatedItem.Type.MOVE, null, null, null, DATE.atTime(9, 0),
                                DATE.atTime(9, 10), 10))),
                        new EstimatedDay(next, next.atTime(9, 10), List.of(new EstimatedItem(1,
                                EstimatedItem.Type.MOVE, null, null, null, next.atTime(9, 0),
                                next.atTime(9, 10), 10)))));
    }

    private static EstimatedItem item(int order, EstimatedItem.Type type, UUID id, MealType meal,
            int startHour, int startMinute, int endHour, int endMinute, Integer minutes) {
        return new EstimatedItem(order, type, id, null, meal, DATE.atTime(startHour, startMinute),
                DATE.atTime(endHour, endMinute), minutes);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
