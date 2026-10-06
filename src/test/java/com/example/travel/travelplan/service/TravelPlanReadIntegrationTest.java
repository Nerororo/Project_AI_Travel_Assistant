package com.example.travel.travelplan.service;

import com.example.travel.global.security.JwtService;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.PlanPlace;
import com.example.travel.travelplan.domain.FoodPreference;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.domain.TravelPlanDay;
import com.example.travel.travelplan.domain.TravelPlanItem;
import com.example.travel.travelplan.domain.TravelPlanShare;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanPatchRequest;
import com.example.travel.travelplan.repository.FoodPreferenceRepository;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.travelplan.repository.TravelPlanShareRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TravelPlanReadIntegrationTest {
    private static final String JWT_SECRET = randomSecret();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    @Container @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("routy.jwt.active-key-id", () -> "test-active");
        registry.add("routy.jwt.keys[0].id", () -> "test-active");
        registry.add("routy.jwt.keys[0].secret", () -> JWT_SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired TravelPlanRepository plans;
    @Autowired TravelPlanDayRepository days;
    @Autowired TravelPlanItemRepository items;
    @Autowired PlanPlaceRepository places;
    @Autowired FoodPreferenceRepository foods;
    @Autowired TravelPlanShareRepository shares;
    @Autowired TravelPlanMutationService mutations;
    @Autowired TransactionTemplate transactions;

    @Test
    void listsOnlyOwnedPlansAndReadsStoredDetailWithoutTransientFields() throws Exception {
        long owner = user();
        long other = user();
        long first = plan(owner, "First");
        long latest = plan(owner, "Latest");
        long foreign = plan(other, "Foreign");
        PlanPlace place = places.saveAndFlush(new PlanPlace(latest, "100", "https://place.map.kakao.com/100",
                PlanPlace.Role.ATTRACTION, "My place", "My note", 60));
        TravelPlanDay day = days.saveAndFlush(new TravelPlanDay(latest, 1, DATE,
                LocalTime.of(9, 0), LocalTime.of(18, 0)));
        items.saveAndFlush(new TravelPlanItem(day.id(), latest, 1, TravelPlanItem.Type.VISIT,
                place.id(), LocalTime.of(9, 0), LocalTime.of(10, 0), null));
        items.saveAndFlush(new TravelPlanItem(day.id(), latest, 2, TravelPlanItem.Type.MEAL,
                null, LocalTime.of(12, 0), LocalTime.of(13, 0), null));
        items.saveAndFlush(new TravelPlanItem(day.id(), latest, 3, TravelPlanItem.Type.MOVE,
                null, LocalTime.of(13, 0), LocalTime.of(13, 20), 20));

        mvc.perform(get("/api/travel-plans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/travel-plans").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].travelPlanId").value(latest))
                .andExpect(jsonPath("$[1].travelPlanId").value(first))
                .andExpect(jsonPath("$[2]").doesNotExist())
                .andExpect(jsonPath("$[0].days").doesNotExist());
        mvc.perform(get("/api/travel-plans/{id}", latest).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region.displayName").value("Region snapshot"))
                .andExpect(jsonPath("$.hotel").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.days[0].items[0].displayName").value("My place"))
                .andExpect(jsonPath("$.days[0].items[0].memo").value("My note"))
                .andExpect(jsonPath("$.days[0].items[1].displayName").value("점심 식사"))
                .andExpect(jsonPath("$.days[0].items[1].planPlaceId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.days[0].items[2].estimatedMinutes").value(20))
                .andExpect(jsonPath("$.warnings").doesNotExist())
                .andExpect(jsonPath("$.coordinate").doesNotExist())
                .andExpect(jsonPath("$.days[0].items[0].kakaoPlaceId").doesNotExist());
        mvc.perform(get("/api/travel-plans/{id}", foreign).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/travel-plans/{id}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL_PLAN_NOT_FOUND"));
    }

    @Test
    void detailReturnsStoredHotelOutsideDayItems() throws Exception {
        long owner = user();
        long planId = plans.saveAndFlush(new TravelPlan(owner, "Two days", "KR-30",
                "Region snapshot", DATE, DATE.plusDays(1), TravelMode.CAR, 15)).id();
        PlanPlace hotel = places.saveAndFlush(new PlanPlace(planId, "300",
                "https://place.map.kakao.com/300", PlanPlace.Role.HOTEL,
                "My hotel", "Quiet room", null));
        days.saveAndFlush(new TravelPlanDay(planId, 1, DATE,
                LocalTime.of(9, 0), LocalTime.of(18, 0)));
        days.saveAndFlush(new TravelPlanDay(planId, 2, DATE.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(18, 0)));

        mvc.perform(get("/api/travel-plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotel.planPlaceId").value(hotel.id()))
                .andExpect(jsonPath("$.hotel.displayName").value("My hotel"))
                .andExpect(jsonPath("$.hotel.memo").value("Quiet room"))
                .andExpect(jsonPath("$.hotel.placeUrl").value("https://place.map.kakao.com/300"))
                .andExpect(jsonPath("$.hotel.kakaoPlaceId").doesNotExist())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].items").isEmpty())
                .andExpect(jsonPath("$.days[1].items").isEmpty());
    }

    @Test
    void patchAllowsOnlyOwnedTextAndRejectsStructuralChanges() throws Exception {
        long owner = user();
        long other = user();
        long planId = plan(owner, "Original");
        long foreignId = plan(other, "Foreign");
        PlanPlace place = places.saveAndFlush(new PlanPlace(planId, "100", "https://place.map.kakao.com/100",
                PlanPlace.Role.HOTEL, "숙소", "Original note", null));
        PlanPlace foreign = places.saveAndFlush(new PlanPlace(foreignId, "200", "https://place.map.kakao.com/200",
                PlanPlace.Role.HOTEL, "Other hotel", null, null));

        mvc.perform(patch("/api/travel-plans/{id}", planId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ignored\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/travel-plans/{id}", planId).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Updated  \",\"placeEdits\":[{\"planPlaceId\":"
                                + place.id() + ",\"displayName\":\"My hotel\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
        mvc.perform(patch("/api/travel-plans/{id}", planId).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeEdits\":[{\"planPlaceId\":" + place.id()
                                + ",\"memo\":null}]}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(places.findById(place.id())).get().satisfies(saved -> {
            org.assertj.core.api.Assertions.assertThat(saved.displayName()).isEqualTo("My hotel");
            org.assertj.core.api.Assertions.assertThat(saved.memo()).isNull();
            org.assertj.core.api.Assertions.assertThat(saved.placeUrl()).isEqualTo("https://place.map.kakao.com/100");
        });
        for (String body : new String[] {
                "{\"travelMode\":\"PUBLIC_TRANSIT\"}",
                "{\"title\":\"Bad\",\"startDate\":\"2026-10-02\"}",
                "{\"placeEdits\":[{\"planPlaceId\":" + place.id() + ",\"stayMinutes\":60}]}",
                "{\"placeEdits\":[{\"planPlaceId\":" + place.id() + ",\"placeUrl\":\"changed\"}]}",
                "{\"placeEdits\":[{\"planPlaceId\":" + foreign.id() + ",\"memo\":\"No\"}]}",
                "{\"placeEdits\":[{\"planPlaceId\":" + place.id() + ",\"memo\":\"A\"},"
                        + "{\"planPlaceId\":" + place.id() + ",\"memo\":\"B\"}]}"
        }) {
            mvc.perform(patch("/api/travel-plans/{id}", planId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        org.assertj.core.api.Assertions.assertThat(plans.findById(planId)).get()
                .extracting(TravelPlan::title).isEqualTo("Updated");
        mvc.perform(patch("/api/travel-plans/{id}", foreignId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/travel-plans/{id}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesWholeAggregateAndRollsBackWhenTransactionFails() throws Exception {
        long owner = user();
        long other = user();
        long planId = plan(owner, "Delete me");
        PlanPlace place = places.saveAndFlush(new PlanPlace(planId, "100", "https://place.map.kakao.com/100",
                PlanPlace.Role.ATTRACTION, "My place", null, 60));
        TravelPlanDay day = days.saveAndFlush(new TravelPlanDay(planId, 1, DATE,
                LocalTime.of(9, 0), LocalTime.of(18, 0)));
        items.saveAndFlush(new TravelPlanItem(day.id(), planId, 1, TravelPlanItem.Type.VISIT,
                place.id(), LocalTime.of(9, 0), LocalTime.of(10, 0), null));
        foods.saveAndFlush(new FoodPreference(planId, "noodles"));
        shares.saveAndFlush(new TravelPlanShare(planId, new byte[32],
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z")));

        mvc.perform(delete("/api/travel-plans/{id}", planId)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/travel-plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactions.execute(status -> {
            mutations.delete(owner, planId);
            throw new IllegalStateException("rollback test");
        })).isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThat(plans.existsById(planId)).isTrue();
        org.assertj.core.api.Assertions.assertThat(items.findByTravelPlanDayIdOrderByItemOrder(day.id())).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(shares.existsById(planId)).isTrue();

        mvc.perform(delete("/api/travel-plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(plans.existsById(planId)).isFalse();
        org.assertj.core.api.Assertions.assertThat(days.findByTravelPlanIdOrderByDayNumber(planId)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(items.findByTravelPlanDayIdOrderByItemOrder(day.id())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(places.findByTravelPlanId(planId)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(foods.findByTravelPlanId(planId)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(shares.existsById(planId)).isFalse();
        mvc.perform(delete("/api/travel-plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentPatchesPreserveBothPlaceEdits() throws Exception {
        long owner = user();
        long planId = plan(owner, "Original");
        PlanPlace place = places.saveAndFlush(new PlanPlace(planId, "100",
                "https://place.map.kakao.com/100", PlanPlace.Role.HOTEL, "Before", null, null));
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<TravelPlanDetailResponse>> second = new AtomicReference<>();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            TravelPlanDetailResponse first = transactions.execute(status -> {
                var response = mutations.patch(owner, planId, edit(place.id(), "After", null));
                second.set(executor.submit(() -> {
                    started.countDown();
                    return mutations.patch(owner, planId, edit(place.id(), null, "Note"));
                }));
                await(started);
                assertThatThrownBy(() -> second.get().get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                return response;
            });
            TravelPlanDetailResponse later = second.get().get(5, TimeUnit.SECONDS);
            assertThat(first.hotel().displayName()).isEqualTo("After");
            assertThat(later.hotel().displayName()).isEqualTo("After");
            assertThat(later.hotel().memo()).isEqualTo("Note");
            assertThat(places.findById(place.id())).get().satisfies(saved -> {
                assertThat(saved.displayName()).isEqualTo("After");
                assertThat(saved.memo()).isEqualTo("Note");
            });
        }
    }

    @Test
    void patchBuildsSuccessfulResponseBeforeConcurrentDelete() throws Exception {
        long owner = user();
        long planId = plan(owner, "Original");
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<?>> deletion = new AtomicReference<>();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            TravelPlanDetailResponse patched = transactions.execute(status -> {
                var response = mutations.patch(owner, planId,
                        TravelPlanPatchRequest.from(Map.of("title", "Updated")));
                deletion.set(executor.submit(() -> {
                    started.countDown();
                    mutations.delete(owner, planId);
                }));
                await(started);
                assertThatThrownBy(() -> deletion.get().get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                return response;
            });
            assertThat(patched.title()).isEqualTo("Updated");
            deletion.get().get(5, TimeUnit.SECONDS);
            assertThat(plans.existsById(planId)).isFalse();
        }
    }

    private static TravelPlanPatchRequest edit(long placeId, String name, String memo) {
        Map<String, Object> values = new java.util.HashMap<>();
        values.put("planPlaceId", placeId);
        if (name != null) values.put("displayName", name);
        if (memo != null) values.put("memo", memo);
        return TravelPlanPatchRequest.from(Map.of("placeEdits", List.of(values)));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent request did not start");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private long user() {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "encoded")).id();
    }

    private long plan(long userId, String title) {
        return plans.saveAndFlush(new TravelPlan(userId, title, "KR-30", "Region snapshot",
                DATE, DATE, TravelMode.CAR, 15)).id();
    }

    private String bearer(long userId) { return "Bearer " + jwt.issue(userId); }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
