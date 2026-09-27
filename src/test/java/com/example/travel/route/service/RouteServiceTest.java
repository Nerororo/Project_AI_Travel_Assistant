package com.example.travel.route.service;

import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.HaversineTravelTimeEstimator;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.algorithm.TravelTimePolicy;
import com.example.travel.route.client.FakeCarRouteClient;
import com.example.travel.route.client.FakePublicTransitRouteClient;
import com.example.travel.route.client.RouteClientException;
import com.example.travel.route.client.RouteClientFailure;
import com.example.travel.route.client.RouteResult;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.user.dto.UsageDenialScope;
import com.example.travel.user.dto.UsageReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(first, second, third)
		);

		assertThat(result.segmentTravelTimes()).containsExactly(
				RouteSegmentTravelTime.found(0),
				RouteSegmentTravelTime.found(10),
				RouteSegmentTravelTime.found(20)
		);
		assertThat(result.fallbackApplied()).isFalse();
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

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(segment())
		);

		assertThat(result.segmentTravelTimes())
				.containsExactly(RouteSegmentTravelTime.found(expectedMinutes));
		assertThat(result.fallbackApplied()).isFalse();
	}

	@Test
	void usesOnlyPublicTransitClientAndPreservesNormalRouteAbsence() {
		RouteSegment first = segment(0.0, 0.0, 1.0, 1.0);
		RouteSegment second = segment(1.0, 1.0, 2.0, 2.0);
		publicTransitClient.willReturnInOrder(RouteResult.found(2_220), RouteResult.notFound());

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.PUBLIC_TRANSIT,
				List.of(first, second)
		);

		assertThat(result.segmentTravelTimes()).containsExactly(
				RouteSegmentTravelTime.found(40),
				RouteSegmentTravelTime.notFound()
		);
		assertThat(result.fallbackApplied()).isFalse();
		assertThat(publicTransitClient.receivedSegments()).containsExactly(first, second);
		assertThat(carClient.callCount()).isZero();
	}

	@Test
	void returnsAnImmutableEmptyResultWithoutCallingAClient() {
		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of()
		);

		assertThat(result.segmentTravelTimes()).isEmpty();
		assertThat(result.fallbackApplied()).isFalse();
		assertThatThrownBy(() -> result.segmentTravelTimes().add(RouteSegmentTravelTime.found(10)))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThat(carClient.callCount()).isZero();
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = RouteClientFailure.class, names = {
			"TIMEOUT", "CONNECTION_FAILED", "PROVIDER_UNAVAILABLE"
	})
	void retriesTransientTechnicalFailureOnceAndUsesProviderResultOnSuccess(
			RouteClientFailure failure
	) {
		RouteSegment routeSegment = segment();
		publicTransitClient.willFailThenReturn(failure, RouteResult.found(601));

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.PUBLIC_TRANSIT,
				List.of(routeSegment)
		);

		assertThat(result.segmentTravelTimes())
				.containsExactly(RouteSegmentTravelTime.found(20));
		assertThat(result.fallbackApplied()).isFalse();
		assertThat(publicTransitClient.receivedSegments())
				.containsExactly(routeSegment, routeSegment);
		assertThat(carClient.callCount()).isZero();
	}

	@Test
	void preservesNormalRouteAbsenceReturnedByRetryWithoutFallback() {
		RouteSegment routeSegment = segment();
		carClient.willFailThenReturn(RouteClientFailure.TIMEOUT, RouteResult.notFound());

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(routeSegment)
		);

		assertThat(result.segmentTravelTimes())
				.containsExactly(RouteSegmentTravelTime.notFound());
		assertThat(result.fallbackApplied()).isFalse();
		assertThat(carClient.receivedSegments()).containsExactly(routeSegment, routeSegment);
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void normalRouteAbsenceDoesNotRequestRetryQuota() {
		AtomicInteger retryReservations = new AtomicInteger();
		carClient.willReturn(RouteResult.notFound());

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(segment()),
				() -> {
					retryReservations.incrementAndGet();
					return UsageReservationResult.success();
				}
		);

		assertThat(result.segmentTravelTimes()).containsExactly(RouteSegmentTravelTime.notFound());
		assertThat(retryReservations).hasValue(0);
		assertThat(carClient.callCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@EnumSource(value = RouteClientFailure.class, names = {
			"TIMEOUT", "CONNECTION_FAILED", "PROVIDER_UNAVAILABLE"
	})
	void fallsBackAllSegmentsAfterOneFailedRetry(RouteClientFailure failure) {
		RouteSegment first = segment(37.5665, 126.9780, 37.5700, 126.9920);
		RouteSegment second = segment(37.5700, 126.9920, 37.5512, 126.9882);
		carClient.willReturnThenFail(RouteResult.found(600), failure);

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(first, second)
		);

		assertThat(result.segmentTravelTimes()).containsExactly(
				fallbackTime(TravelMode.CAR, first),
				fallbackTime(TravelMode.CAR, second)
		);
		assertThat(result.fallbackApplied()).isTrue();
		assertThat(carClient.receivedSegments()).containsExactly(first, second, second);
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = RouteClientFailure.class, names = {
			"INVALID_REQUEST", "AUTHENTICATION_FAILED", "ACCESS_DENIED",
			"RATE_LIMITED", "INVALID_RESPONSE"
	})
	void doesNotRetryOrFallbackNonTransientFailure(RouteClientFailure failure) {
		AtomicInteger retryReservations = new AtomicInteger();
		carClient.willFailWith(failure);

		assertThatThrownBy(() -> service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(segment()),
				() -> {
					retryReservations.incrementAndGet();
					return UsageReservationResult.success();
				}))
				.isInstanceOfSatisfying(RouteClientException.class,
						exception -> assertThat(exception.failure()).isEqualTo(failure));
		assertThat(retryReservations).hasValue(0);
		assertThat(carClient.callCount()).isEqualTo(1);
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void retryQuotaDenialKeepsEveryDeniedScopeOnTheFallbackResult() {
		RouteSegment routeSegment = segment();
		carClient.willFailWith(RouteClientFailure.TIMEOUT);

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(routeSegment),
				() -> UsageReservationResult.denied(
						31L,
						Set.of(UsageDenialScope.USER, UsageDenialScope.SERVICE)
				)
		);

		assertThat(result.fallbackReason()).isEqualTo(RouteFallbackReason.QUOTA_UNAVAILABLE);
		assertThat(result.quotaDeniedScopes())
				.containsExactlyInAnyOrder(UsageDenialScope.USER, UsageDenialScope.SERVICE);
		assertThat(carClient.callCount()).isEqualTo(1);
	}

	@Test
	void releasesUncalledInitialSegmentsAfterAnEarlyTechnicalFallback() {
		RouteSegment first = segment(0.0, 0.0, 1.0, 1.0);
		RouteSegment second = segment(1.0, 1.0, 2.0, 2.0);
		RouteSegment third = segment(2.0, 2.0, 3.0, 3.0);
		AtomicLong released = new AtomicLong();
		carClient.willFailWith(RouteClientFailure.TIMEOUT);

		RouteTravelTimeResult result = service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(first, second, third),
				UsageReservationResult::success,
				released::addAndGet
		);

		assertThat(carClient.receivedSegments()).containsExactly(first, first);
		assertThat(released).hasValue(2L);
		assertThat(result.fallbackReason()).isEqualTo(RouteFallbackReason.TECHNICAL_FAILURE);
	}

	@Test
	void releasesUncalledInitialSegmentsBeforePropagatingANonTransientFailure() {
		RouteSegment first = segment(0.0, 0.0, 1.0, 1.0);
		RouteSegment second = segment(1.0, 1.0, 2.0, 2.0);
		AtomicLong released = new AtomicLong();
		carClient.willFailWith(RouteClientFailure.INVALID_REQUEST);

		assertThatThrownBy(() -> service.findEstimatedTravelTimes(
				TravelMode.CAR,
				List.of(first, second),
				UsageReservationResult::success,
				released::addAndGet
		)).isInstanceOf(RouteClientException.class);

		assertThat(carClient.receivedSegments()).containsExactly(first);
		assertThat(released).hasValue(1L);
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

	private RouteSegmentTravelTime fallbackTime(TravelMode travelMode, RouteSegment segment) {
		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(
				TravelTimePolicy.defaultFor(travelMode)
		);
		return RouteSegmentTravelTime.found(estimator.estimateMinutes(
				new Coordinate(segment.origin().latitude(), segment.origin().longitude()),
				new Coordinate(segment.destination().latitude(), segment.destination().longitude())
		));
	}
}
