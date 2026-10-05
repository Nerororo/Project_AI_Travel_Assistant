package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.service.PlanningPlaceSelectionService;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.service.RouteWarning;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.domain.TravelPlan;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.RouteNotFoundDetails;
import com.example.travel.travelplan.dto.TravelConditions;
import com.example.travel.travelplan.dto.TravelPlanCreateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import com.example.travel.travelplan.repository.PlanPlaceRepository;
import com.example.travel.travelplan.repository.TravelPlanDayRepository;
import com.example.travel.travelplan.repository.TravelPlanItemRepository;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.RequestExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelPlanCreateApiServiceTest {
    @Test
    void missingRouteReleasesRequestAndNeverStartsPersistence() {
        var estimates = mock(TravelPlanEstimateApiService.class);
        var selections = mock(PlanningPlaceSelectionService.class);
        var regions = mock(PlaceSearchRegionService.class);
        var calculations = mock(TravelPlanCompletionCalculationService.class);
        var saves = mock(TravelPlanCompletionSaveService.class);
        var transactions = mock(TransactionTemplate.class);
        var executions = mock(RequestExecutionService.class);
        var service = new TravelPlanCreateApiService(estimates, selections, regions, calculations,
                saves, transactions, executions, mock(TravelPlanRepository.class),
                mock(TravelPlanDayRepository.class), mock(TravelPlanItemRepository.class),
                mock(PlanPlaceRepository.class));
        LocalDate date = LocalDate.of(2026, 10, 1);
        UUID key = new UUID(0, 2);
        var conditions = TravelConditions.withDefaultMealTravelBuffer(
                new TravelPeriod(date, date), TravelMode.CAR,
                List.of(new DailyActivityWindow(date, LocalTime.of(9, 0), LocalTime.of(10, 0))));
        var command = new EstimateCommand(7, "KR-30", conditions, "start", "end", List.of(), null);
        var request = new TravelPlanCreateApiRequest("Trip", "KR-30", TravelMode.CAR, date, date,
                "start", "end", List.of(new TravelPlanEstimateApiRequest.Day(date,
                        LocalTime.of(9, 0), LocalTime.of(10, 0))), List.of(), null, null,
                null, List.of("menu"), List.of());
        var lease = new RequestExecutionLease(7, UsageFeature.TRAVEL_PLAN_CREATE, key,
                Instant.parse("2026-10-01T00:10:00Z"));
        var failure = new ApiException(ErrorCode.ROUTE_NOT_FOUND,
                new RouteNotFoundDetails(date, 1, TravelMode.CAR),
                List.of("CHANGE_ORDER", "REMOVE_PLACE", "CHANGE_TRAVEL_MODE"), null);
        when(executions.startTravelPlanCreation(7, key)).thenReturn(lease);
        when(estimates.resolve(eq(7L), any())).thenReturn(command);
        when(regions.findById("KR-30")).thenReturn(Optional.of(new PlaceSearchRegion(
                "KR-30", "대전광역시", null, true, false, null, null, null)));
        when(calculations.calculate(eq(command), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.create(7, key, request)).isSameAs(failure);
        verify(executions).releaseAfterFailure(lease);
        verify(saves, never()).save(any());
        verify(transactions, never()).execute(any());
    }

    @Test
    void calculatesBeforeSavingAndReturnsWarningOnlyInResponse() {
        var estimates = mock(TravelPlanEstimateApiService.class);
        var selections = mock(PlanningPlaceSelectionService.class);
        var regions = mock(PlaceSearchRegionService.class);
        var calculations = mock(TravelPlanCompletionCalculationService.class);
        var saves = mock(TravelPlanCompletionSaveService.class);
        var transactions = mock(TransactionTemplate.class);
        var executions = mock(RequestExecutionService.class);
        var plans = mock(TravelPlanRepository.class);
        var days = mock(TravelPlanDayRepository.class);
        var items = mock(TravelPlanItemRepository.class);
        var places = mock(PlanPlaceRepository.class);
        var service = new TravelPlanCreateApiService(estimates, selections, regions, calculations,
                saves, transactions, executions, plans, days, items, places);
        LocalDate date = LocalDate.of(2026, 10, 1);
        UUID key = new UUID(0, 1);
        var window = new DailyActivityWindow(date, LocalTime.of(9, 0), LocalTime.of(10, 0));
        var conditions = TravelConditions.withDefaultMealTravelBuffer(
                new TravelPeriod(date, date), TravelMode.CAR, List.of(window));
        var command = new EstimateCommand(7, "KR-30", conditions, "start", "end", List.of(), null);
        var request = new TravelPlanCreateApiRequest("Trip", "KR-30", TravelMode.CAR, date, date,
                "start", "end", List.of(new TravelPlanEstimateApiRequest.Day(date,
                        LocalTime.of(9, 0), LocalTime.of(10, 0))), List.of(), null, null,
                null, List.of("menu"), List.of());
        var lease = new RequestExecutionLease(7, UsageFeature.TRAVEL_PLAN_CREATE, key,
                Instant.parse("2026-10-01T00:10:00Z"));
        when(executions.startTravelPlanCreation(7, key)).thenReturn(lease);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        when(estimates.resolve(eq(7L), any())).thenReturn(command);
        when(regions.findById("KR-30")).thenReturn(Optional.of(new PlaceSearchRegion(
                "KR-30", "대전광역시", null, true, false, null, null, null)));
        when(calculations.calculate(eq(command), any())).thenReturn(
                new TravelPlanCompletionCalculationService.CalculationResult(
                        List.of(), List.of(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED), null));
        when(saves.save(any())).thenReturn(42L);
        when(plans.findById(42L)).thenReturn(Optional.of(new TravelPlan(7L, "Trip", "KR-30",
                "대전광역시", date, date, TravelMode.CAR, 15)));
        when(executions.markSucceeded(lease)).thenReturn(true);

        var response = service.create(7, key, request);

        assertThat(response.travelPlanId()).isEqualTo(42);
        assertThat(response.warnings()).containsExactly("ESTIMATED_TRAVEL_TIMES_USED");
        var order = inOrder(calculations, saves, executions);
        order.verify(executions).startTravelPlanCreation(7, key);
        order.verify(calculations).calculate(eq(command), any());
        order.verify(saves).save(any());
        order.verify(executions).markSucceeded(lease);
    }
}
