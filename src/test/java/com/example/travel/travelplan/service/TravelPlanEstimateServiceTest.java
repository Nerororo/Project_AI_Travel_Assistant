package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.TravelBoundarySelection;
import com.example.travel.place.service.SelectionTokenService.InvalidSelectionTokenException;
import com.example.travel.place.service.TravelBoundarySelectionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.domain.DailyActivityWindow;
import com.example.travel.travelplan.domain.TravelPeriod;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateResult;
import com.example.travel.travelplan.dto.EstimateVisit;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.PlanCapacityDetails;
import com.example.travel.travelplan.dto.TravelConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelPlanEstimateServiceTest {

	private static final LocalDate FIRST = LocalDate.of(2026, 10, 1);
	private static final Coordinate CENTER = new Coordinate(36.0, 127.0);
	private static final Coordinate EAST = new Coordinate(36.0, 127.01);
	private static final Coordinate WEST = new Coordinate(36.0, 126.99);
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private TravelBoundarySelectionService boundaries;
	private TravelPlanEstimateService service;

	@BeforeEach
	void setUp() {
		boundaries = mock(TravelBoundarySelectionService.class);
		service = new TravelPlanEstimateService(boundaries);
		when(boundaries.verify("start", 7, "KR-30")).thenReturn(boundary("1", CENTER));
		when(boundaries.verify("end", 7, "KR-30")).thenReturn(boundary("1", CENTER));
	}

	@Test
	void autoAssignmentIsDeterministicAndKeepsEveryVisitExactlyOnce() {
		EstimateVisit a = visit(A, EAST, 90, null, null);
		EstimateVisit b = visit(B, WEST, 90, null, null);
		EstimateCommand forward = command(2, List.of(a, b), LocalTime.of(9, 0),
				LocalTime.of(20, 0), CENTER);
		EstimateCommand reversed = command(2, List.of(b, a), LocalTime.of(9, 0),
				LocalTime.of(20, 0), CENTER);

		EstimateResult first = service.estimate(forward);
		EstimateResult second = service.estimate(reversed);

		assertThat(first).isEqualTo(second);
		assertThat(first.routeVerified()).isFalse();
		assertThat(first.days()).hasSize(2);
		assertThat(first.days().stream().flatMap(day -> day.items().stream())
				.filter(item -> item.type() == EstimatedItem.Type.VISIT)
				.map(EstimatedItem::clientPlaceId)).containsExactlyInAnyOrder(A, B);
		verify(boundaries, times(2)).verify("start", 7, "KR-30");
		verify(boundaries, times(2)).verify("end", 7, "KR-30");
	}

	@Test
	void manualAssignmentPreservesDayAndOrder() {
		EstimateCommand command = command(1, List.of(
				visit(B, EAST, 90, FIRST, 2), visit(A, WEST, 90, FIRST, 1)),
				LocalTime.of(9, 0), LocalTime.of(20, 0), null);

		EstimateResult result = service.estimate(command);

		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.VISIT)
				.map(EstimatedItem::clientPlaceId)).containsExactly(A, B);
	}

	@Test
	void sevenDayRouteKeepsDistinctStartHotelAndEndSegmentsIncludingZeroMinuteMoves() {
		when(boundaries.verify("end", 7, "KR-30")).thenReturn(boundary("2", WEST));
		List<EstimateVisit> visits = IntStream.range(0, 7)
				.mapToObj(index -> visit(new UUID(0, index + 1), CENTER, 90,
						FIRST.plusDays(index), 1)).toList();
		EstimateCommand command = command(7, visits, LocalTime.of(9, 0),
				LocalTime.of(21, 0), EAST);

		EstimateResult result = service.estimate(command);

		assertThat(result.days()).hasSize(7);
		assertThat(moves(result, 0)).containsExactly(0, 10);
		assertThat(moves(result, 3)).containsExactly(10, 10);
		assertThat(moves(result, 6)).containsExactly(10, 10);
		assertThat(result.days().stream().flatMap(day -> day.items().stream())
				.filter(item -> item.type() == EstimatedItem.Type.VISIT)).hasSize(7);
	}

	@Test
	void includedMealDoesNotExtendLongVisitOrAddBufferTwice() {
		EstimateCommand command = command(1,
				List.of(visit(A, CENTER, 180, FIRST, 1)),
				LocalTime.of(11, 0), LocalTime.of(16, 0), null);

		EstimateResult result = service.estimate(command);

		assertThat(result.days().getFirst().plannedEndTime().toLocalTime())
				.isEqualTo(LocalTime.of(14, 0));
		assertThat(result.days().getFirst().items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MEAL)).hasSize(1);
	}

	@Test
	void capacityFailureReportsFirstDateWithoutAnyPartialPlanOrPlaceData() {
		EstimateCommand command = command(1,
				List.of(visit(A, CENTER, 480, FIRST, 1)),
				LocalTime.of(9, 0), LocalTime.of(12, 0), null);

		assertThatThrownBy(() -> service.estimate(command))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_CAPACITY_EXCEEDED);
					assertThat(exception.details()).isInstanceOf(PlanCapacityDetails.class);
					PlanCapacityDetails details = (PlanCapacityDetails) exception.details();
					assertThat(details.date()).isEqualTo(FIRST);
					assertThat(details.exceededMinutes()).isPositive();
					assertThat(details.toString()).doesNotContain(A.toString());
				});
	}

	@Test
	void mealWindowThatCannotHoldTravelBufferIsCapacityFailure() {
		EstimateCommand command = command(1, List.of(), LocalTime.of(13, 0),
				LocalTime.of(14, 0), null);

		assertThatThrownBy(() -> service.estimate(command))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLAN_CAPACITY_EXCEEDED);
					PlanCapacityDetails details = (PlanCapacityDetails) exception.details();
					assertThat(details.date()).isEqualTo(FIRST);
					assertThat(details.exceededMinutes()).isPositive();
				});
	}

	@Test
	void partialAssignmentAndDuplicateOrdersAreRejectedBeforeTokenVerification() {
		EstimateCommand partial = command(2, List.of(
				visit(A, CENTER, 90, FIRST, 1), visit(B, EAST, 90, null, null)),
				LocalTime.of(9, 0), LocalTime.of(20, 0), CENTER);
		assertThatThrownBy(() -> service.estimate(partial))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

		EstimateCommand duplicate = command(1, List.of(
				visit(A, CENTER, 90, FIRST, 1), visit(B, EAST, 90, FIRST, 1)),
				LocalTime.of(9, 0), LocalTime.of(20, 0), null);
		assertThatThrownBy(() -> service.estimate(duplicate))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void invalidBoundaryTokenStopsEstimation() {
		when(boundaries.verify("start", 7, "KR-30")).thenThrow(new InvalidSelectionTokenException());
		EstimateCommand command = command(1, List.of(), LocalTime.of(9, 0),
				LocalTime.of(20, 0), null);

		assertThatThrownBy(() -> service.estimate(command))
				.isInstanceOf(InvalidSelectionTokenException.class);
	}

	private static EstimateCommand command(int days, List<EstimateVisit> visits,
			LocalTime start, LocalTime end, Coordinate hotel) {
		TravelPeriod period = new TravelPeriod(FIRST, FIRST.plusDays(days - 1));
		TravelConditions conditions = TravelConditions.withDefaultMealTravelBuffer(period,
				TravelMode.CAR, period.dates().stream()
						.map(date -> new DailyActivityWindow(date, start, end)).toList());
		return new EstimateCommand(7, "KR-30", conditions, "start", "end", visits, hotel);
	}

	private static EstimateVisit visit(UUID id, Coordinate coordinate, int stay,
			LocalDate day, Integer order) {
		return new EstimateVisit(id, coordinate, "사용자 이름", stay, day, order);
	}

	private static TravelBoundarySelection boundary(String id, Coordinate coordinate) {
		return new TravelBoundarySelection(id, URI.create("https://place.map.kakao.com/" + id),
				coordinate.latitude(), coordinate.longitude());
	}

	private static List<Integer> moves(EstimateResult result, int dayIndex) {
		return result.days().get(dayIndex).items().stream()
				.filter(item -> item.type() == EstimatedItem.Type.MOVE)
				.map(EstimatedItem::estimatedMinutes).toList();
	}
}
