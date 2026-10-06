package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.security.JwtService;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.place.service.SelectionTokenService;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.dto.TravelPlanCreateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TravelPlanCreateFlowIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final String REGION = "KR-30";
    private static final String JWT_SECRET = randomSecret();

    @Container @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("routy.jwt.active-key-id", () -> "test-active");
        registry.add("routy.jwt.keys[0].id", () -> "test-active");
        registry.add("routy.jwt.keys[0].secret", () -> JWT_SECRET);
    }

    @Autowired TravelPlanCreateApiService service;
    @Autowired SelectionTokenService tokens;
    @Autowired UserRepository users;
    @Autowired TravelPlanRepository plans;
    @Autowired PlanPlaceRepository places;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;

    @Test
    void httpCreateRequiresAuthenticationAndReturnsStoredPlan() throws Exception {
        long userId = user();
        UUID requestId = UUID.randomUUID();
        String body = mapper.writeValueAsString(request(userId, LocalTime.of(11, 0)));

        mvc.perform(post("/api/travel-plans").header("Idempotency-Key", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/travel-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issue(userId))
                        .header("Idempotency-Key", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.warnings[0]").value("ESTIMATED_TRAVEL_TIMES_USED"))
                .andExpect(jsonPath("$.hotel").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.days[0].items[?(@.type == 'VISIT')]").isNotEmpty())
                .andExpect(jsonPath("$.coordinate").doesNotExist());
        mvc.perform(post("/api/travel-plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issue(userId))
                        .header("Idempotency-Key", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_COMPLETED"));
        assertThat(plans.findByUserId(userId)).hasSize(1);
    }

    @Test
    void savesCalculatedPlanAndBlocksTheSameRequestId() {
        long userId = user();
        UUID requestId = UUID.randomUUID();
        TravelPlanCreateApiRequest request = request(userId, LocalTime.of(11, 0));

        var response = service.create(userId, requestId, request);

        assertThat(response.travelPlanId()).isPositive();
        assertThat(response.hotel()).isNull();
        assertThat(response.warnings()).containsExactly("ESTIMATED_TRAVEL_TIMES_USED");
        assertThat(response.days()).singleElement().satisfies(day ->
                assertThat(day.items()).extracting(item -> item.type()).contains("VISIT", "MOVE"));
        assertThat(plans.findByUserId(userId)).hasSize(1);
        assertThatThrownBy(() -> service.create(userId, requestId, request))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.REQUEST_ALREADY_COMPLETED));
        assertThat(plans.findByUserId(userId)).hasSize(1);
    }

    @Test
    void capacityFailureDoesNotSaveAndCanRetryWithTheSameRequestId() {
        long userId = user();
        UUID requestId = UUID.randomUUID();
        TravelPlanCreateApiRequest shortDay = request(userId, LocalTime.of(9, 30));

        assertThatThrownBy(() -> service.create(userId, requestId, shortDay))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.PLAN_CAPACITY_EXCEEDED));
        assertThat(plans.findByUserId(userId)).isEmpty();

        var response = service.create(userId, requestId, request(userId, LocalTime.of(11, 0)));
        assertThat(response.travelPlanId()).isPositive();
    }

    @Test
    void selectedAndUnselectedLunchesKeepTheirSeparateStorageRules() {
        long userId = user();
        String restaurant = token(userId, PlaceRole.RESTAURANT, "900003", 36.002, 127.002);
        var selected = service.create(userId, UUID.randomUUID(), request(userId, LocalTime.of(15, 0),
                List.of(new TravelPlanCreateApiRequest.Meal(DATE, MealType.LUNCH,
                        restaurant, "My lunch", "My memo"))));
        var selectedMeal = selected.days().getFirst().items().stream()
                .filter(item -> item.type().equals("MEAL")).findFirst().orElseThrow();
        assertThat(selectedMeal.planPlaceId()).isNotNull();
        assertThat(selectedMeal.displayName()).isEqualTo("My lunch");
        assertThat(places.findByTravelPlanId(selected.travelPlanId())).hasSize(2);

        var unselected = service.create(userId, UUID.randomUUID(), request(userId, LocalTime.of(15, 0),
                List.of(new TravelPlanCreateApiRequest.Meal(DATE, MealType.LUNCH,
                        null, "점심 식사", null))));
        var unselectedMeal = unselected.days().getFirst().items().stream()
                .filter(item -> item.type().equals("MEAL")).findFirst().orElseThrow();
        assertThat(unselectedMeal.planPlaceId()).isNull();
        assertThat(unselectedMeal.displayName()).isEqualTo("점심 식사");
        assertThat(places.findByTravelPlanId(unselected.travelPlanId())).hasSize(1);
    }

    @Test
    void multiDayTransitPlanStoresTheUserSelectedHotelName() {
        long userId = user();
        String boundary = token(userId, PlaceRole.TRAVEL_BOUNDARY, "900004", 36.0, 127.0);
        String hotel = token(userId, PlaceRole.HOTEL, "900005", 36.001, 127.001);
        var nextDate = DATE.plusDays(1);
        var request = new TravelPlanCreateApiRequest("Transit trip", REGION, TravelMode.PUBLIC_TRANSIT,
                DATE, nextDate, boundary, boundary,
                List.of(new TravelPlanEstimateApiRequest.Day(DATE, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        new TravelPlanEstimateApiRequest.Day(nextDate, LocalTime.of(9, 0), LocalTime.of(10, 0))),
                List.of(), hotel, "My hotel", null, List.of("menu"), List.of());

        var response = service.create(userId, UUID.randomUUID(), request);

        assertThat(response.travelMode()).isEqualTo(TravelMode.PUBLIC_TRANSIT);
        assertThat(response.days()).hasSize(2);
        assertThat(response.warnings()).containsExactly("ESTIMATED_TRAVEL_TIMES_USED");
        var savedHotel = places.findByTravelPlanId(response.travelPlanId()).getFirst();
        assertThat(response.hotel().planPlaceId()).isEqualTo(savedHotel.id());
        assertThat(response.hotel().displayName()).isEqualTo("My hotel");
        assertThat(response.hotel().memo()).isNull();
        assertThat(response.hotel().placeUrl()).isEqualTo("https://place.map.kakao.com/900005");
        assertThat(response.days()).flatExtracting(day -> day.items())
                .noneMatch(item -> item.type().equals("STAY"));
    }

    private long user() {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "encoded")).id();
    }

    private TravelPlanCreateApiRequest request(long userId, LocalTime endTime) {
        return request(userId, endTime, List.of());
    }

    private TravelPlanCreateApiRequest request(long userId, LocalTime endTime,
            List<TravelPlanCreateApiRequest.Meal> meals) {
        String boundary = token(userId, PlaceRole.TRAVEL_BOUNDARY, "900001", 36.0, 127.0);
        String attraction = token(userId, PlaceRole.ATTRACTION, "900002", 36.001, 127.001);
        return new TravelPlanCreateApiRequest(" My trip ", REGION, TravelMode.CAR, DATE, DATE,
                boundary, boundary,
                List.of(new TravelPlanEstimateApiRequest.Day(DATE, LocalTime.of(9, 0), endTime)),
                List.of(new TravelPlanEstimateApiRequest.Place(new UUID(0, 1), attraction,
                        "My stop", 30, DATE, 1)),
                null, null, null, List.of("menu"), meals);
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
