package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.FakeCarRouteClient;
import com.example.travel.route.client.FakePublicTransitRouteClient;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.route.client.RouteClientException;
import com.example.travel.route.client.RouteClientFailure;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.service.RouteObservationEvent;
import com.example.travel.route.service.RouteObservationOutcome;
import com.example.travel.route.service.RouteFallbackReason;
import com.example.travel.route.service.RouteSegmentTravelTime;
import com.example.travel.route.service.RouteTravelTimeResult;
import com.example.travel.route.service.RouteVerificationResult;
import com.example.travel.route.service.RouteVerificationService;
import com.example.travel.route.service.RouteQuotaService;
import com.example.travel.route.service.RouteService;
import com.example.travel.route.service.RouteWarning;
import com.example.travel.user.dto.UsageReservationLease;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.MealType;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.PlanCapacityDetails;
import com.example.travel.travelplan.dto.RouteNotFoundDetails;
import com.example.travel.travelplan.dto.TravelConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelPlanCompletionCalculationServiceTest {
	private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
	private static final Coordinate START = new Coordinate(36.0, 127.0);
	private static final Coordinate FIRST = new Coordinate(36.0, 127.01);
	private static final Coordinate SECOND = new Coordinate(36.0, 127.02);
	private static final Coordinate END = new Coordinate(36.0, 127.03);
	private static final UUID A = new UUID(0, 1);
	private static final UUID B = new UUID(0, 2);

	private TravelBoundarySelectionService boundaries;
	private RouteVerificationService routes;
	private TravelPlanCompletionCalculationService service;

	@BeforeEach
	void setUp() {
		boundaries = mock(TravelBoundarySelectionService.class);
		routes = mock(RouteVerificationService.class);
		when(boundaries.verify("start", 7, "KR-30")).thenReturn(boundary("1", START));
		when(boundaries.verify("end", 7, "KR-30")).thenReturn(boundary("2", END));
		service = new TravelPlanCompletionCalculationService(
				new TravelPlanEstimateService(boundaries), boundaries, routes);
	}

	@Test
	void preservesConfirmedVisitOrderAndUsesOnlyAdjacentProviderDurations() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0), List.of(
				visit(B, SECOND, 2), visit(A, FIRST, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(20, 30, 10));

		var result = service.calculate(command, Map.of());

		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.VISIT)
				.map(EstimatedItem::clientPlaceId)).containsExactly(A, B);
		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MOVE)
				.map(EstimatedItem::estimatedMinutes)).containsExactly(20, 30, 10);
		assertThat(result.days().getFirst().plannedEndTime().toLocalTime()).isEqualTo(LocalTime.of(11, 0));
		ArgumentCaptor<List<RouteSegment>> captured = ArgumentCaptor.forClass(List.class);
		verify(routes).verify(eq(7L), eq(TravelMode.CAR), captured.capture());
		assertThat(captured.getValue()).containsExactly(
				segment(START, FIRST), segment(FIRST, SECOND), segment(SECOND, END));
	}

	@Test
	void selectedRestaurantBecomesARealWaypointAndFallbackWarningStaysRequestScoped() {
		Coordinate restaurant = new Coordinate(36.01, 127.01);
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(15, 0),
				List.of(visit(A, FIRST, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(new RouteVerificationResult(
					RouteTravelTimeResult.fallback(List.of(
							RouteSegmentTravelTime.found(10), RouteSegmentTravelTime.found(20),
							RouteSegmentTravelTime.found(10)), RouteFallbackReason.TECHNICAL_FAILURE),
					List.of(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED),
					new RouteObservationEvent(TravelMode.CAR,
							RouteObservationOutcome.TECHNICAL_FAILURE_FALLBACK, Set.of())));

		var result = service.calculate(command, Map.of(DATE, Map.of(MealType.LUNCH, restaurant)));

		assertThat(result.warnings()).containsExactly(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED);
		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MOVE)).hasSize(3);
		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MEAL)).hasSize(1);
		ArgumentCaptor<List<RouteSegment>> captured = ArgumentCaptor.forClass(List.class);
		verify(routes).verify(eq(7L), eq(TravelMode.CAR), captured.capture());
		assertThat(captured.getValue()).containsExactly(
				segment(START, restaurant), segment(restaurant, FIRST), segment(FIRST, END));
	}

	@Test
	void providerOverrunFailsWithoutDeletingVisitsOrShorteningStays() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(11, 0),
				List.of(visit(A, FIRST, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(50, 50));

		assertThatThrownBy(() -> service.calculate(command, Map.of()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_CAPACITY_EXCEEDED);
					assertThat(((PlanCapacityDetails) exception.details()).plannedEndTime())
							.isEqualTo(LocalTime.of(11, 10));
				});
	}

	@Test
	void missingRouteBlocksCompletionWithoutFallback() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0),
				List.of(visit(A, FIRST, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(new RouteVerificationResult(RouteTravelTimeResult.verified(List.of(
					RouteSegmentTravelTime.notFound())),
					List.of(), new RouteObservationEvent(TravelMode.CAR,
						RouteObservationOutcome.PROVIDER_VERIFIED, Set.of())));

		assertMissingRoute(command, Map.of(), DATE, 1, TravelMode.CAR);
	}

	@Test
	void realRouteVerificationStopsAtFirstMissingMoveAndReturnsItsQuota() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0),
				List.of(visit(A, FIRST, 1)));
		FakeCarRouteClient carClient = new FakeCarRouteClient();
		carClient.willReturnThenFail(RouteResult.notFound(), RouteClientFailure.TIMEOUT);
		RouteQuotaService quota = mock(RouteQuotaService.class);
		UsageReservationLease lease = UsageReservationLease.acquired(Instant.EPOCH);
		when(quota.reserve(eq(7L), eq(TravelMode.CAR), anyList())).thenReturn(lease);
		service = new TravelPlanCompletionCalculationService(new TravelPlanEstimateService(boundaries),
				boundaries, new RouteVerificationService(quota,
					new RouteService(carClient, new FakePublicTransitRouteClient())));

		assertMissingRoute(command, Map.of(), DATE, 1, TravelMode.CAR);
		assertThat(carClient.callCount()).isEqualTo(1);
		verify(quota).release(7L, TravelMode.CAR, lease, 1L);
	}

	@Test
	void truncatedVerifiedResultWithoutMissingRouteIsRejected() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0),
				List.of(visit(A, FIRST, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(10));

		assertThatThrownBy(() -> service.calculate(command, Map.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("route segment count changed");
	}

	@Test
	void identifiesMiddleAndFinalMovesByFinalItemOrder() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0), List.of(
				visit(A, FIRST, 1), visit(B, SECOND, 2)));
		for (int index = 1; index <= 2; index++) {
			when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
					.thenReturn(missing(TravelMode.CAR, 3, index));
			assertMissingRoute(command, Map.of(), DATE, index == 1 ? 3 : 5, TravelMode.CAR);
		}
	}

	@Test
	void identifiesSelectedMealDetourMovesAroundTheMeal() {
		Coordinate restaurant = new Coordinate(36.01, 127.01);
		EstimateCommand command = command(LocalTime.of(11, 0), LocalTime.of(16, 0),
				List.of(new EstimateVisit(A, FIRST, "사용자 이름", 180, DATE, 1)));
		for (int index = 1; index <= 2; index++) {
			when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
					.thenReturn(missing(TravelMode.CAR, 4, index));
			assertMissingRoute(command, Map.of(DATE, Map.of(MealType.LUNCH, restaurant)),
					DATE, index == 1 ? 3 : 5, TravelMode.CAR);
		}
	}

	@Test
	void identifiesSecondDayHotelBoundaryWithoutPlaceIdentity() {
		Coordinate hotel = new Coordinate(36.01, 127.01);
		TravelPeriod period = new TravelPeriod(DATE, DATE.plusDays(1));
		TravelConditions conditions = TravelConditions.withDefaultMealTravelBuffer(period,
				TravelMode.PUBLIC_TRANSIT, period.dates().stream()
						.map(date -> new DailyActivityWindow(date, LocalTime.of(9, 0), LocalTime.of(11, 0)))
						.toList());
		EstimateCommand command = new EstimateCommand(7, "KR-30", conditions, "start", "end",
				List.of(visit(A, FIRST, 1), new EstimateVisit(B, SECOND, "사용자 이름", 30,
						DATE.plusDays(1), 1)), hotel);
		when(routes.verify(eq(7L), eq(TravelMode.PUBLIC_TRANSIT), anyList()))
				.thenReturn(missing(TravelMode.PUBLIC_TRANSIT, 4, 2));
		assertMissingRoute(command, Map.of(), DATE.plusDays(1), 1, TravelMode.PUBLIC_TRANSIT);
	}

	@Test
	void identifiesRepeatedHotelVisitAsItsOwnMove() {
		Coordinate hotel = new Coordinate(36.01, 127.01);
		TravelPeriod period = new TravelPeriod(DATE, DATE.plusDays(2));
		TravelConditions conditions = TravelConditions.withDefaultMealTravelBuffer(period,
				TravelMode.CAR, period.dates().stream()
						.map(date -> new DailyActivityWindow(date, LocalTime.of(9, 0), LocalTime.of(10, 0)))
						.toList());
		EstimateCommand command = new EstimateCommand(7, "KR-30", conditions, "start", "end",
				List.of(), hotel);
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(missing(TravelMode.CAR, 3, 1));
		assertMissingRoute(command, Map.of(), DATE.plusDays(1), 1, TravelMode.CAR);
	}

	@Test
	void providerContractFailureIsMappedWithoutProviderDetails() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0),
				List.of(visit(A, FIRST, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenThrow(new RouteClientException(RouteClientFailure.INVALID_RESPONSE));

		assertThatThrownBy(() -> service.calculate(command, Map.of()))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
					assertThat(exception.getMessage()).doesNotContain("INVALID_RESPONSE");
				});
	}

	@Test
	void automaticAssignmentIsRejectedBeforeRouteUse() {
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(12, 0),
				List.of(new EstimateVisit(A, FIRST, "사용자 이름", 30, null, null)));
		assertThatThrownBy(() -> service.calculate(command, Map.of()))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(routes, never()).verify(eq(7L), eq(TravelMode.CAR), anyList());
	}

	@Test
	void actualShorterRoutesCanMakeAnEstimatedOverrunFit() {
		Coordinate distant = new Coordinate(37.0, 128.0);
		EstimateCommand command = command(LocalTime.of(9, 0), LocalTime.of(10, 0),
				List.of(visit(A, distant, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(10, 10));

		assertThat(service.calculate(command, Map.of()).days().getFirst().plannedEndTime().toLocalTime())
				.isEqualTo(LocalTime.of(9, 50));
	}

	@Test
	void selectedRestaurantUsesVerifiedTravelInsteadOfSyntheticMealBuffer() {
		EstimateCommand command = command(LocalTime.of(13, 0), LocalTime.of(14, 0), List.of());
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(0, 0));

		var result = service.calculate(command, Map.of(DATE, Map.of(MealType.LUNCH, FIRST)));

		assertThat(result.days().getFirst().plannedEndTime().toLocalTime())
				.isEqualTo(LocalTime.of(14, 0));
		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MEAL)
				.map(EstimatedItem::startTime))
				.containsExactly(DATE.atTime(13, 0));
	}

	@Test
	void selectedRestaurantRoundTripFitsInsideLongVisitWithoutExtendingItsStay() {
		Coordinate restaurant = new Coordinate(36.01, 127.01);
		EstimateCommand command = command(LocalTime.of(11, 0), LocalTime.of(16, 0),
				List.of(new EstimateVisit(A, FIRST, "사용자 이름", 180, DATE, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(0, 10, 10, 0));

		var result = service.calculate(command, Map.of(DATE, Map.of(MealType.LUNCH, restaurant)));

		assertThat(result.days().getFirst().plannedEndTime().toLocalTime())
				.isEqualTo(LocalTime.of(14, 0));
		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MOVE)).hasSize(4);
		ArgumentCaptor<List<RouteSegment>> captured = ArgumentCaptor.forClass(List.class);
		verify(routes).verify(eq(7L), eq(TravelMode.CAR), captured.capture());
		assertThat(captured.getValue()).containsExactly(segment(START, FIRST),
				segment(FIRST, restaurant), segment(restaurant, FIRST), segment(FIRST, END));
	}

	@Test
	void distantRestaurantIsPlacedOutsideVisitWhenRoundTripCannotFit() {
		Coordinate restaurant = new Coordinate(37.0, 128.0);
		EstimateCommand command = command(LocalTime.of(11, 0), LocalTime.of(17, 0),
				List.of(new EstimateVisit(A, FIRST, "사용자 이름", 180, DATE, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(10, 10, 10));

		var result = service.calculate(command, Map.of(DATE, Map.of(MealType.LUNCH, restaurant)));

		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MOVE)).hasSize(3);
		ArgumentCaptor<List<RouteSegment>> captured = ArgumentCaptor.forClass(List.class);
		verify(routes).verify(eq(7L), eq(TravelMode.CAR), captured.capture());
		assertThat(captured.getValue()).hasSize(3);
	}

	@Test
	void restaurantDetourThatMissesLunchWindowIsPlacedBeforeVisit() {
		Coordinate visit = new Coordinate(36.0, 127.7);
		Coordinate restaurant = new Coordinate(36.0, 128.1);
		EstimateCommand command = command(LocalTime.of(11, 0), LocalTime.of(17, 0),
				List.of(new EstimateVisit(A, visit, "사용자 이름", 180, DATE, 1)));
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(10, 10, 10));

		service.calculate(command, Map.of(DATE, Map.of(MealType.LUNCH, restaurant)));

		ArgumentCaptor<List<RouteSegment>> captured = ArgumentCaptor.forClass(List.class);
		verify(routes).verify(eq(7L), eq(TravelMode.CAR), captured.capture());
		assertThat(captured.getValue()).containsExactly(
				segment(START, restaurant), segment(restaurant, visit), segment(visit, END));
	}

	@Test
	void multiDaySegmentsKeepHotelAsEachDayBoundary() {
		Coordinate hotel = new Coordinate(36.01, 127.01);
		TravelPeriod period = new TravelPeriod(DATE, DATE.plusDays(1));
		TravelConditions conditions = TravelConditions.withDefaultMealTravelBuffer(period,
				TravelMode.CAR, period.dates().stream()
						.map(date -> new DailyActivityWindow(date, LocalTime.of(9, 0), LocalTime.of(11, 0)))
						.toList());
		EstimateCommand command = new EstimateCommand(7, "KR-30", conditions, "start", "end",
				List.of(visit(A, FIRST, 1), new EstimateVisit(B, SECOND, "사용자 이름", 30,
						DATE.plusDays(1), 1)), hotel);
		when(routes.verify(eq(7L), eq(TravelMode.CAR), anyList()))
				.thenReturn(verified(10, 10, 10, 10));

		var result = service.calculate(command, Map.of());

		assertThat(result.days()).hasSize(2);
		ArgumentCaptor<List<RouteSegment>> captured = ArgumentCaptor.forClass(List.class);
		verify(routes).verify(eq(7L), eq(TravelMode.CAR), captured.capture());
		assertThat(captured.getValue()).containsExactly(
				segment(START, FIRST), segment(FIRST, hotel),
				segment(hotel, SECOND), segment(SECOND, END));
	}

	private static EstimateCommand command(LocalTime start, LocalTime end, List<EstimateVisit> visits) {
		TravelPeriod period = new TravelPeriod(DATE, DATE);
		TravelConditions conditions = TravelConditions.withDefaultMealTravelBuffer(period,
				TravelMode.CAR, List.of(new DailyActivityWindow(DATE, start, end)));
		return new EstimateCommand(7, "KR-30", conditions, "start", "end", visits, null);
	}

	private static EstimateVisit visit(UUID id, Coordinate coordinate, int order) {
		return new EstimateVisit(id, coordinate, "사용자 이름", 30, DATE, order);
	}

	private static TravelBoundarySelection boundary(String id, Coordinate coordinate) {
		return new TravelBoundarySelection(id, URI.create("https://place.map.kakao.com/" + id),
				coordinate.latitude(), coordinate.longitude());
	}

	private static RouteSegment segment(Coordinate from, Coordinate to) {
		return new RouteSegment(new RouteSegment.Endpoint(from.latitude(), from.longitude()),
				new RouteSegment.Endpoint(to.latitude(), to.longitude()));
	}

	private static RouteVerificationResult verified(int... minutes) {
		List<RouteSegmentTravelTime> times = java.util.Arrays.stream(minutes)
				.mapToObj(RouteSegmentTravelTime::found).map(value -> (RouteSegmentTravelTime) value).toList();
		return new RouteVerificationResult(RouteTravelTimeResult.verified(times), List.of(),
				new RouteObservationEvent(TravelMode.CAR,
						RouteObservationOutcome.PROVIDER_VERIFIED, Set.of()));
	}

	private static RouteVerificationResult missing(TravelMode mode, int count, int missingIndex) {
		if (missingIndex < 0 || missingIndex >= count) {
			throw new IllegalArgumentException("missingIndex must identify a requested segment");
		}
		List<RouteSegmentTravelTime> times = new java.util.ArrayList<>();
		for (int index = 0; index <= missingIndex; index++) {
			times.add(index == missingIndex ? RouteSegmentTravelTime.notFound()
					: RouteSegmentTravelTime.found(10));
		}
		return new RouteVerificationResult(RouteTravelTimeResult.verified(times), List.of(),
				new RouteObservationEvent(mode, RouteObservationOutcome.PROVIDER_VERIFIED, Set.of()));
	}

	private void assertMissingRoute(EstimateCommand command,
			Map<LocalDate, Map<MealType, Coordinate>> restaurants,
			LocalDate date, int moveOrder, TravelMode mode) {
		assertThatThrownBy(() -> service.calculate(command, restaurants))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
					assertThat(exception.details()).isEqualTo(new RouteNotFoundDetails(date, moveOrder, mode));
					assertThat(exception.adjustments()).containsExactly(
							"CHANGE_ORDER", "REMOVE_PLACE", "CHANGE_TRAVEL_MODE");
				});
	}
}
