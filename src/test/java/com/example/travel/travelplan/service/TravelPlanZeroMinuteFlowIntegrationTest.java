package com.example.travel.travelplan.service;

import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.place.service.SelectionTokenService;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.CarRouteClient;
import com.example.travel.route.client.RouteResult;
import com.example.travel.travelplan.domain.TravelPlanItem;
import com.example.travel.travelplan.dto.TravelPlanCreateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class TravelPlanZeroMinuteFlowIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final String REGION = "KR-30";
    private static final AtomicInteger routeCalls = new AtomicInteger();
    private static final String JWT_SECRET = randomSecret();

    @Container @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("routy.jwt.active-key-id", () -> "test-active");
        registry.add("routy.jwt.keys[0].id", () -> "test-active");
        registry.add("routy.jwt.keys[0].secret", () -> JWT_SECRET);
    }

    @TestConfiguration
    static class Routes {
        @Bean @Primary
        CarRouteClient zeroThenTwentyMinutes() {
            return segment -> RouteResult.found(routeCalls.getAndIncrement() == 0 ? 0 : 601);
        }
    }

    @Autowired TravelPlanCreateApiService creates;
    @Autowired TravelPlanReadService reads;
    @Autowired SelectionTokenService tokens;
    @Autowired UserRepository users;
    @Autowired TravelPlanDayRepository days;
    @Autowired TravelPlanItemRepository items;

    @Test
    void zeroMinuteProviderLegIsOmittedFromSavedAndReturnedItems() {
        routeCalls.set(0);
        long userId = users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "encoded")).id();
        String boundary = token(userId, PlaceRole.TRAVEL_BOUNDARY, "900001", 36.0, 127.0);
        String attraction = token(userId, PlaceRole.ATTRACTION, "900002", 36.001, 127.001);
        var request = new TravelPlanCreateApiRequest("My trip", REGION, TravelMode.CAR, DATE, DATE,
                boundary, boundary,
                List.of(new TravelPlanEstimateApiRequest.Day(DATE, LocalTime.of(9, 0), LocalTime.of(10, 0))),
                List.of(new TravelPlanEstimateApiRequest.Place(new UUID(0, 1), attraction,
                        "My stop", 30, DATE, 1)),
                null, null, null, List.of("menu"), List.of());

        var created = creates.create(userId, UUID.randomUUID(), request);
        var detail = reads.detail(userId, created.travelPlanId());
        var dayId = days.findByTravelPlanIdOrderByDayNumber(created.travelPlanId()).getFirst().id();
        var saved = items.findByTravelPlanDayIdOrderByItemOrder(dayId);

        assertThat(routeCalls.get()).isEqualTo(2);
        assertThat(created.warnings()).isEmpty();
        assertThat(created.days().getFirst().items()).extracting(item -> item.type())
                .containsExactly("VISIT", "MOVE");
        assertThat(created.days().getFirst().items()).extracting(item -> item.order())
                .containsExactly(1, 2);
        assertThat(created.days().getFirst().items().getLast().estimatedMinutes()).isEqualTo(20);
        assertThat(detail.days().getFirst().items()).isEqualTo(created.days().getFirst().items());
        assertThat(saved).extracting(TravelPlanItem::itemType)
                .containsExactly(TravelPlanItem.Type.VISIT, TravelPlanItem.Type.MOVE);
        assertThat(saved).extracting(TravelPlanItem::itemOrder).containsExactly(1, 2);
        assertThat(saved.getLast().estimatedMinutes()).isEqualTo(20);
    }

    private String token(long userId, PlaceRole role, String placeId, double latitude, double longitude) {
        return tokens.issue(new SelectionTokenPlace(userId, REGION, role, placeId,
                URI.create("https://place.map.kakao.com/" + placeId), latitude, longitude));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
