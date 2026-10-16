package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.FakeCarRouteClient;
import com.example.travel.route.client.FakePublicTransitRouteClient;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.route.service.RouteService;
import com.example.travel.route.service.RouteVerificationService;
import com.example.travel.route.service.RouteQuotaService;
import com.example.travel.user.dto.UsageReservationLease;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.MealSlotPolicy;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.TravelConditions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;

class TravelPlanTimeScenariosTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final Coordinate CENTER = new Coordinate(36, 127);

    @Test
    void providerRoundingAndCompletionCapacityHoldForBothTravelModes() {
        int[][] boundariesInSeconds = {{0, 0}, {1, 10}, {599, 10}, {600, 10}, {601, 20},
                {1199, 20}, {1200, 20}, {1201, 30}, {3599, 60}, {3600, 60}, {3601, 70}};
        for (TravelMode mode : TravelMode.values()) {
            for (int[] boundaryCase : boundariesInSeconds) {
                var car = new FakeCarRouteClient();
                var transit = new FakePublicTransitRouteClient();
                car.willReturn(RouteResult.found(boundaryCase[0]));
                transit.willReturn(RouteResult.found(boundaryCase[0]));
                var segment = new RouteSegment(new RouteSegment.Endpoint(36, 127), new RouteSegment.Endpoint(36, 127));
                var quota = mock(RouteQuotaService.class);
                when(quota.reserve(eq(7L), eq(mode), anyList())).thenReturn(UsageReservationLease.acquired(Instant.EPOCH));
                var routes = new RouteVerificationService(quota, new RouteService(car, transit));
                var times = routes.verify(7, mode, List.of(segment, segment)).travelTimes();
                assertThat(times.segmentTravelTimes()).allSatisfy(time -> assertThat(time)
                        .isInstanceOfSatisfying(com.example.travel.route.service.RouteSegmentTravelTime.Found.class,
                                found -> assertThat(found.estimatedMinutes()).isEqualTo(boundaryCase[1])));
                var selections = mock(TravelBoundarySelectionService.class);
                var point = new TravelBoundarySelection("1", URI.create("https://place.map.kakao.com/1"), 36, 127);
                when(selections.verify("start", 7, "KR-30")).thenReturn(point);
                when(selections.verify("end", 7, "KR-30")).thenReturn(point);
                var command = new EstimateCommand(7, "KR-30", new TravelConditions(new TravelPeriod(DATE, DATE),
                        mode, List.of(new DailyActivityWindow(DATE, LocalTime.of(9, 0), LocalTime.of(11, 0))), 15),
                        "start", "end", List.of(new EstimateVisit(new UUID(0, 1), CENTER, "사용자 이름", 30, DATE, 1)), null);
                var service = new TravelPlanCompletionCalculationService(new TravelPlanEstimateService(selections), selections, routes);
                int elapsed = 30 + 2 * boundaryCase[1];
                if (elapsed <= 120) {
                    assertThat(service.calculate(command, java.util.Map.of()).days().getFirst().plannedEndTime())
                            .isEqualTo(DATE.atTime(9, 0).plusMinutes(elapsed));
                } else {
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.calculate(command, java.util.Map.of()))
                            .isInstanceOfSatisfying(ApiException.class, failure -> {
                                assertThat(failure.errorCode()).isEqualTo(ErrorCode.PLAN_CAPACITY_EXCEEDED);
                                assertThat((com.example.travel.travelplan.dto.PlanCapacityDetails) failure.details())
                                        .extracting(com.example.travel.travelplan.dto.PlanCapacityDetails::exceededMinutes)
                                        .isEqualTo((long) elapsed - 120);
                            });
                }
            }
        }
    }

    @Test
    void comparesCapacityAgainstAnIndependentSinglePlaceTimeBudget() {
        var boundaries = mock(TravelBoundarySelectionService.class);
        var boundary = new TravelBoundarySelection("1", URI.create("https://place.map.kakao.com/1"), 36, 127);
        when(boundaries.verify("start", 7, "KR-30")).thenReturn(boundary);
        when(boundaries.verify("end", 7, "KR-30")).thenReturn(boundary);
        var service = new TravelPlanEstimateService(boundaries);
        int checked = 0;
        var mismatches = new java.util.ArrayList<String>();
        for (int start : new int[] {9 * 60, 11 * 60, 12 * 60, 13 * 60, 17 * 60}) {
            for (int end : new int[] {13 * 60, 13 * 60 + 5, 13 * 60 + 10, 13 * 60 + 14,
                    14 * 60, 16 * 60, 20 * 60, 23 * 60}) {
                if (start >= end) continue;
                for (int stay : new int[] {30, 60, 120, 150, 180, 300, 480}) {
                    for (int buffer : new int[] {0, 15, 60}) {
                        var window = new DailyActivityWindow(DATE, clock(start), clock(end));
                        var conditions = new TravelConditions(
                                new TravelPeriod(DATE, DATE), TravelMode.CAR, List.of(window), buffer);
                        int earliestFinish = earliestFinish(window, stay, buffer);
                        boolean feasible = earliestFinish <= end;
                        for (int count : new int[] {1, 2, 5}) {
                            if (stay % count != 0 || stay / count < 30 || stay / count % 10 != 0) continue;
                            for (boolean automatic : new boolean[] {false, true}) {
                                var visits = new java.util.ArrayList<EstimateVisit>();
                                for (int index = 0; index < count; index++) visits.add(new EstimateVisit(
                                        new UUID(0, index + 1), CENTER, "사용자 이름", stay / count,
                                        automatic ? null : DATE, automatic ? null : index + 1));
                                var command = new EstimateCommand(7, "KR-30", conditions, "start", "end", visits, null);
                                String scenario = "start=" + start + ", end=" + end + ", stay=" + stay
                                        + ", buffer=" + buffer + ", places=" + count + ", auto=" + automatic;
                                try {
                                    var day = service.estimate(command).days().getFirst();
                                    if (!feasible) mismatches.add("unexpected success: " + scenario);
                                    assertThat(day.plannedEndTime()).as(scenario).isBeforeOrEqualTo(DATE.atTime(clock(end)));
                                    assertThat(day.items().stream().filter(item -> item.type() == EstimatedItem.Type.VISIT)
                                            .mapToLong(item -> Duration.between(item.startTime(), item.endTime()).toMinutes()).sum())
                                            .as(scenario).isEqualTo(stay);
                                    assertThat(day.items().stream().filter(item -> item.type() == EstimatedItem.Type.VISIT)
                                            .map(EstimatedItem::clientPlaceId).distinct()).as(scenario).hasSize(count);
                                    for (int index = 1; index < day.items().size(); index++) {
                                        assertThat(day.items().get(index).startTime()).as(scenario)
                                                .isAfterOrEqualTo(day.items().get(index - 1).endTime());
                                    }
                                } catch (ApiException failure) {
                                    assertThat(failure.errorCode()).as(scenario).isEqualTo(ErrorCode.PLAN_CAPACITY_EXCEEDED);
                                    if (feasible) mismatches.add("unexpected rejection: " + scenario + ", earliestFinish=" + earliestFinish);
                                }
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(2772);
        assertThat(mismatches).isEmpty();
    }

    // With no inter-place travel, sightseeing can fill free time around the earliest allowed meals.
    private static int earliestFinish(DailyActivityWindow window, int stay, int buffer) {
        int cursor = window.startTime().toSecondOfDay() / 60;
        for (MealType type : MealType.values()) {
            if (MealSlotPolicy.createSlot(window, type).isEmpty()) continue;
            int before = Math.min(stay, Math.max(0, type.allowedStart().toSecondOfDay() / 60 - buffer - cursor));
            cursor += before;
            stay -= before;
            int mealStart = Math.max(cursor + buffer, type.allowedStart().toSecondOfDay() / 60);
            if (mealStart + 60 > type.allowedEnd().toSecondOfDay() / 60) return Integer.MAX_VALUE;
            cursor = mealStart + 60 + buffer;
        }
        return cursor + stay;
    }

    private static LocalTime clock(int minutes) { return LocalTime.of(minutes / 60, minutes % 60); }
}
