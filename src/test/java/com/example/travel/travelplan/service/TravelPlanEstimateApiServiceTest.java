package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.service.PlanningPlaceSelectionService;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.dto.EstimateCommand;
import com.example.travel.travelplan.dto.EstimateResult;
import com.example.travel.travelplan.dto.EstimatedDay;
import com.example.travel.travelplan.dto.EstimatedItem;
import com.example.travel.travelplan.dto.TravelPlanEstimateApiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TravelPlanEstimateApiServiceTest {

	private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
	private static final UUID VISIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private PlanningPlaceSelectionService places;
	private TravelPlanEstimateService estimator;
	private TravelPlanEstimateApiService service;

	@BeforeEach
	void setUp() {
		places = mock(PlanningPlaceSelectionService.class);
		estimator = mock(TravelPlanEstimateService.class);
		service = new TravelPlanEstimateApiService(places, estimator);
		when(places.verifyAttraction("attraction-token", 7L, "KR-30"))
				.thenReturn(selection("1", 36.0, 127.0));
		when(places.verifyHotel("hotel-token", 7L, "KR-30"))
				.thenReturn(selection("2", 36.1, 127.1));
		when(estimator.estimate(any())).thenReturn(result());
	}

	@Test
	void usesVerifiedAttractionCoordinateAndKeepsTokensOutOfResponse() {
		var response = service.estimate(7L, request(1, "attraction-token", null));

		ArgumentCaptor<EstimateCommand> command = ArgumentCaptor.forClass(EstimateCommand.class);
		verify(estimator).estimate(command.capture());
		assertThat(command.getValue().visits()).hasSize(1);
		assertThat(command.getValue().visits().getFirst().coordinate().latitude()).isEqualTo(36.0);
		assertThat(command.getValue().visits().getFirst().coordinate().longitude()).isEqualTo(127.0);
		assertThat(command.getValue().hotelCoordinate()).isNull();
		assertThat(command.getValue().startBoundarySelectionToken()).isEqualTo("start-token");
		assertThat(response.routeVerified()).isFalse();
		assertThat(response.days().getFirst().items().getFirst().startTime()).isEqualTo("09:00");
		assertThat(response.toString()).doesNotContain("attraction-token", "start-token", "127.0");
		verify(places, never()).verifyHotel(any(), anyLong(), any());
	}

	@Test
	void multiDayRequestVerifiesHotelRoleBeforeCalculation() {
		service.estimate(7L, request(2, "attraction-token", "hotel-token"));

		ArgumentCaptor<EstimateCommand> command = ArgumentCaptor.forClass(EstimateCommand.class);
		verify(estimator).estimate(command.capture());
		verify(places).verifyHotel("hotel-token", 7L, "KR-30");
		assertThat(command.getValue().hotelCoordinate().latitude()).isEqualTo(36.1);
	}

	@Test
	void structuralErrorIsRejectedBeforePlaceTokenVerification() {
		var invalid = request(1, "attraction-token", null);
		var duplicateFoods = new TravelPlanEstimateApiRequest(invalid.regionId(), invalid.travelMode(),
				invalid.startDate(), invalid.endDate(), invalid.startBoundarySelectionToken(),
				invalid.endBoundarySelectionToken(), invalid.days(), invalid.places(),
				invalid.hotelSelectionToken(), invalid.mealTravelBufferMinutes(), List.of("메뉴", " 메뉴 "));
		assertThatThrownBy(() -> service.estimate(7L, duplicateFoods))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verifyNoInteractions(estimator);
		verify(places, never()).verifyAttraction(any(), anyLong(), any());
	}

	@Test
	void partialOrderDuplicateIdAndMissingHotelAreValidationErrorsBeforeVerification() {
		var valid = request(1, "attraction-token", null);
		var partialOrder = new TravelPlanEstimateApiRequest.Place(VISIT_ID, "attraction-token",
				"사용자 이름", 90, DATE, null);
		var partial = new TravelPlanEstimateApiRequest(valid.regionId(), valid.travelMode(),
				valid.startDate(), valid.endDate(), valid.startBoundarySelectionToken(),
				valid.endBoundarySelectionToken(), valid.days(), List.of(partialOrder), null, null,
				valid.foods());
		var duplicate = new TravelPlanEstimateApiRequest(valid.regionId(), valid.travelMode(),
				valid.startDate(), valid.endDate(), valid.startBoundarySelectionToken(),
				valid.endBoundarySelectionToken(), valid.days(),
				List.of(valid.places().getFirst(), valid.places().getFirst()), null, null, valid.foods());
		var missingHotel = request(2, "attraction-token", null);
		var dayTripWithBlankHotel = request(1, "attraction-token", " ");

		for (TravelPlanEstimateApiRequest invalid : List.of(
				partial, duplicate, missingHotel, dayTripWithBlankHotel)) {
			assertThatThrownBy(() -> service.estimate(7L, invalid))
					.isInstanceOfSatisfying(ApiException.class,
							exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		}
		verifyNoInteractions(estimator);
		verify(places, never()).verifyAttraction(any(), anyLong(), any());
	}

	@Test
	void wrongPlaceRoleStopsCalculationWithValidationError() {
		when(places.verifyAttraction("wrong-role", 7L, "KR-30"))
				.thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));

		assertThatThrownBy(() -> service.estimate(7L, request(1, "wrong-role", null)))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verifyNoInteractions(estimator);
	}

	private static TravelPlanEstimateApiRequest request(int dayCount, String attractionToken,
			String hotelToken) {
		LocalDate end = DATE.plusDays(dayCount - 1);
		List<TravelPlanEstimateApiRequest.Day> days = DATE.datesUntil(end.plusDays(1))
				.map(date -> new TravelPlanEstimateApiRequest.Day(date, LocalTime.of(9, 0),
						LocalTime.of(20, 0))).toList();
		return new TravelPlanEstimateApiRequest("KR-30", TravelMode.CAR, DATE, end,
				"start-token", "end-token", days,
				List.of(new TravelPlanEstimateApiRequest.Place(VISIT_ID, attractionToken,
						"사용자 이름", 90, null, null)), hotelToken, null, List.of("메뉴"));
	}

	private static PlanningPlaceSelection selection(String id, double latitude, double longitude) {
		return new PlanningPlaceSelection(id, URI.create("https://place.map.kakao.com/" + id),
				latitude, longitude);
	}

	private static EstimateResult result() {
		LocalDateTime start = DATE.atTime(9, 0);
		return new EstimateResult(false, List.of(new EstimatedDay(DATE, start.plusMinutes(90),
				List.of(new EstimatedItem(1, EstimatedItem.Type.VISIT, VISIT_ID, "사용자 이름",
						null, start, start.plusMinutes(90), null)))));
	}
}
