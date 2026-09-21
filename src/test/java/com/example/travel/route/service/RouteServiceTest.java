package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.FakeCarRouteClient;
import com.example.travel.route.client.FakePublicTransitRouteClient;
import com.example.travel.route.client.RouteClientException;
import com.example.travel.route.client.RouteClientFailure;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteServiceTest {

	private FakeCarRouteClient carClient;
	private FakePublicTransitRouteClient publicTransitClient;
	private RouteService service;

	@BeforeEach
	void setUp() {
		carClient = new FakeCarRouteClient();
		publicTransitClient = new FakePublicTransitRouteClient();
		service = new RouteService(carClient, publicTransitClient);
	}

	@Test
	void delegatesCarRouteToOnlyTheCarClient() {
		RouteSegment segment = segment();
		RouteResult expected = RouteResult.found(601);
		carClient.willReturn(expected);

		RouteResult actual = service.findRoute(TravelMode.CAR, segment);

		assertThat(actual).isSameAs(expected);
		assertThat(carClient.lastSegment()).isSameAs(segment);
		assertThat(carClient.callCount()).isEqualTo(1);
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void delegatesPublicTransitRouteToOnlyThePublicTransitClient() {
		RouteSegment segment = segment();
		RouteResult expected = RouteResult.found(1_200);
		publicTransitClient.willReturn(expected);

		RouteResult actual = service.findRoute(TravelMode.PUBLIC_TRANSIT, segment);

		assertThat(actual).isSameAs(expected);
		assertThat(publicTransitClient.lastSegment()).isSameAs(segment);
		assertThat(publicTransitClient.callCount()).isEqualTo(1);
		assertThat(carClient.callCount()).isZero();
	}

	@Test
	void preservesNormalRouteAbsenceWithoutRetryOrFallback() {
		carClient.willReturn(RouteResult.notFound());

		RouteResult result = service.findRoute(TravelMode.CAR, segment());

		assertThat(result).isInstanceOf(RouteResult.NotFound.class);
		assertThat(carClient.callCount()).isEqualTo(1);
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = RouteClientFailure.class, names = {
			"TIMEOUT", "CONNECTION_FAILED", "PROVIDER_UNAVAILABLE"
	})
	void propagatesTechnicalFailureWithoutRetryOrFallback(RouteClientFailure failure) {
		publicTransitClient.willFailWith(failure);

		assertThatThrownBy(() -> service.findRoute(TravelMode.PUBLIC_TRANSIT, segment()))
				.isInstanceOfSatisfying(RouteClientException.class,
						exception -> assertThat(exception.failure()).isEqualTo(failure));
		assertThat(publicTransitClient.callCount()).isEqualTo(1);
		assertThat(carClient.callCount()).isZero();
	}

	@Test
	void verifiesAllAdjacentCarSegmentsInOrderAndRoundsWithoutFixedBuffer() {
		RouteSegment first = segment(0.0, 0.0, 1.0, 1.0);
		RouteSegment second = segment(1.0, 1.0, 2.0, 2.0);
		RouteSegment third = segment(2.0, 2.0, 3.0, 3.0);
		carClient.willReturnInOrder(
				RouteResult.found(0),
				RouteResult.found(600),
				RouteResult.found(601)
		);

		List<RouteSegmentTravelTime> result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(first, second, third)
		);

		assertThat(result).containsExactly(
				RouteSegmentTravelTime.found(0),
				RouteSegmentTravelTime.found(10),
				RouteSegmentTravelTime.found(20)
		);
		assertThat(carClient.receivedSegments()).containsExactly(first, second, third);
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@ParameterizedTest
	@CsvSource({
			"1, 10",
			"599, 10",
			"600, 10",
			"601, 20",
			"1199, 20",
			"1200, 20"
	})
	void roundsProviderSecondsUpAtEveryTenMinuteBoundary(long durationSeconds, int expectedMinutes) {
		carClient.willReturn(RouteResult.found(durationSeconds));

		List<RouteSegmentTravelTime> result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(segment())
		);

		assertThat(result).containsExactly(RouteSegmentTravelTime.found(expectedMinutes));
	}

	@Test
	void usesOnlyPublicTransitClientAndPreservesNormalRouteAbsence() {
		RouteSegment first = segment(0.0, 0.0, 1.0, 1.0);
		RouteSegment second = segment(1.0, 1.0, 2.0, 2.0);
		publicTransitClient.willReturnInOrder(RouteResult.found(2_220), RouteResult.notFound());

		List<RouteSegmentTravelTime> result = service.findEstimatedTravelTimes(
				TravelMode.PUBLIC_TRANSIT,
				List.of(first, second)
		);

		assertThat(result).containsExactly(
				RouteSegmentTravelTime.found(40),
				RouteSegmentTravelTime.notFound()
		);
		assertThat(publicTransitClient.receivedSegments()).containsExactly(first, second);
		assertThat(carClient.callCount()).isZero();
	}

	@Test
	void returnsAnImmutableEmptyResultWithoutCallingAClient() {
		List<RouteSegmentTravelTime> result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of()
		);

		assertThat(result).isEmpty();
		assertThatThrownBy(() -> result.add(RouteSegmentTravelTime.found(10)))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThat(carClient.callCount()).isZero();
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void rejectsInvalidBatchArgumentsBeforeCallingClients() {
		List<RouteSegment> segmentWithNull = new ArrayList<>();
		segmentWithNull.add(null);

		assertThatNullPointerException()
				.isThrownBy(() -> service.findEstimatedTravelTimes(null, List.of()));
		assertThatNullPointerException()
				.isThrownBy(() -> service.findEstimatedTravelTimes(TravelMode.CAR, null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.findEstimatedTravelTimes(TravelMode.CAR, segmentWithNull));
		assertThat(carClient.callCount()).isZero();
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void rejectsMissingDependenciesAndArgumentsBeforeCallingClients() {
		assertThatNullPointerException()
				.isThrownBy(() -> new RouteService(null, publicTransitClient));
		assertThatNullPointerException()
				.isThrownBy(() -> new RouteService(carClient, null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.findRoute(null, segment()));
		assertThatNullPointerException()
				.isThrownBy(() -> service.findRoute(TravelMode.CAR, null));
		assertThat(carClient.callCount()).isZero();
		assertThat(publicTransitClient.callCount()).isZero();
	}

	private RouteSegment segment() {
		return segment(0.0, 0.0, 1.0, 1.0);
	}

	private RouteSegment segment(double originLatitude, double originLongitude,
			double destinationLatitude, double destinationLongitude) {
		return new RouteSegment(
				new RouteSegment.Endpoint(originLatitude, originLongitude),
				new RouteSegment.Endpoint(destinationLatitude, destinationLongitude)
		);
	}
}
