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
import com.example.travel.user.dto.UsageReservationResult;
import com.example.travel.user.dto.UsageDenialScope;
import com.example.travel.user.dto.UsageReservationLease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RouteVerificationServiceTest {

	private RouteQuotaService quotaService;
	private FakeCarRouteClient carClient;
	private FakePublicTransitRouteClient publicTransitClient;
	private RouteVerificationService service;

	@BeforeEach
	void setUp() {
		quotaService = mock(RouteQuotaService.class);
		carClient = new FakeCarRouteClient();
		publicTransitClient = new FakePublicTransitRouteClient();
		service = new RouteVerificationService(
				quotaService,
				new RouteService(carClient, publicTransitClient)
		);
	}

	@Test
	void quotaDenialFallsBackEveryCarSegmentWithoutCallingAProvider() {
		RouteSegment first = segment(37.5665, 126.9780, 37.5700, 126.9920);
		RouteSegment second = segment(37.5700, 126.9920, 37.5512, 126.9882);
		List<RouteSegment> segments = List.of(first, second);
		when(quotaService.reserve(7L, TravelMode.CAR, segments))
				.thenReturn(denied(31L, UsageDenialScope.USER));

		RouteVerificationResult result = service.verify(7L, TravelMode.CAR, segments);

		assertThat(result.travelTimes().segmentTravelTimes()).containsExactly(
				fallbackTime(TravelMode.CAR, first),
				fallbackTime(TravelMode.CAR, second)
		);
		assertThat(result.travelTimes().fallbackApplied()).isTrue();
		assertThat(result.warnings()).containsExactly(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED);
		assertThat(result.observationEvent()).isEqualTo(new RouteObservationEvent(
				TravelMode.CAR,
				RouteObservationOutcome.QUOTA_FALLBACK,
				Set.of(UsageDenialScope.USER)
		));
		assertThat(carClient.callCount()).isZero();
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void quotaDenialKeepsPublicTransitFallbackAndObservationSeparate() {
		RouteSegment routeSegment = segment(35.0, 128.0, 35.1, 128.1);
		when(quotaService.reserve(8L, TravelMode.PUBLIC_TRANSIT, List.of(routeSegment)))
				.thenReturn(denied(61L, UsageDenialScope.SERVICE));

		RouteVerificationResult result = service.verify(
				8L,
				TravelMode.PUBLIC_TRANSIT,
				List.of(routeSegment)
		);

		assertThat(result.travelTimes().segmentTravelTimes())
				.containsExactly(fallbackTime(TravelMode.PUBLIC_TRANSIT, routeSegment));
		assertThat(result.observationEvent()).isEqualTo(new RouteObservationEvent(
				TravelMode.PUBLIC_TRANSIT,
				RouteObservationOutcome.QUOTA_FALLBACK,
				Set.of(UsageDenialScope.SERVICE)
		));
		assertThat(carClient.callCount()).isZero();
		assertThat(publicTransitClient.callCount()).isZero();
	}

	@Test
	void acquiredQuotaUsesTheProviderWithoutWarning() {
		RouteSegment routeSegment = segment(0.0, 0.0, 1.0, 1.0);
		when(quotaService.reserve(9L, TravelMode.CAR, List.of(routeSegment)))
				.thenReturn(acquired());
		carClient.willReturn(RouteResult.found(601));

		RouteVerificationResult result = service.verify(9L, TravelMode.CAR, List.of(routeSegment));

		assertThat(result.travelTimes().segmentTravelTimes())
				.containsExactly(RouteSegmentTravelTime.found(20));
		assertThat(result.travelTimes().fallbackApplied()).isFalse();
		assertThat(result.warnings()).isEmpty();
		assertThat(result.observationEvent()).isEqualTo(new RouteObservationEvent(
				TravelMode.CAR,
				RouteObservationOutcome.PROVIDER_VERIFIED,
				Set.of()
		));
		assertThat(carClient.callCount()).isEqualTo(1);
	}

	@Test
	void technicalFailureUsesTheSameWarningWithADifferentObservationOutcome() {
		RouteSegment routeSegment = segment(37.5, 127.0, 37.6, 127.1);
		when(quotaService.reserve(10L, TravelMode.CAR, List.of(routeSegment)))
				.thenReturn(acquired());
		when(quotaService.reserve(10L, TravelMode.CAR, 1L))
				.thenReturn(acquired());
		carClient.willFailWith(RouteClientFailure.TIMEOUT);

		RouteVerificationResult result = service.verify(10L, TravelMode.CAR, List.of(routeSegment));

		assertThat(result.travelTimes().segmentTravelTimes())
				.containsExactly(fallbackTime(TravelMode.CAR, routeSegment));
		assertThat(result.warnings()).containsExactly(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED);
		assertThat(result.observationEvent()).isEqualTo(new RouteObservationEvent(
				TravelMode.CAR,
				RouteObservationOutcome.TECHNICAL_FAILURE_FALLBACK,
				Set.of()
		));
		assertThat(carClient.callCount()).isEqualTo(2);
		verify(quotaService).reserve(10L, TravelMode.CAR, List.of(routeSegment));
		verify(quotaService).reserve(10L, TravelMode.CAR, 1L);
	}

	@Test
	void retrySuccessReservesOneAdditionalCallAndReturnsAProviderResult() {
		RouteSegment routeSegment = segment(36.1, 127.1, 36.2, 127.2);
		when(quotaService.reserve(12L, TravelMode.PUBLIC_TRANSIT, List.of(routeSegment)))
				.thenReturn(acquired());
		when(quotaService.reserve(12L, TravelMode.PUBLIC_TRANSIT, 1L))
				.thenReturn(acquired());
		publicTransitClient.willFailThenReturn(RouteClientFailure.TIMEOUT, RouteResult.found(601));

		RouteVerificationResult result = service.verify(
				12L,
				TravelMode.PUBLIC_TRANSIT,
				List.of(routeSegment)
		);

		assertThat(result.travelTimes().segmentTravelTimes())
				.containsExactly(RouteSegmentTravelTime.found(20));
		assertThat(result.travelTimes().fallbackApplied()).isFalse();
		assertThat(result.warnings()).isEmpty();
		assertThat(result.observationEvent()).isEqualTo(new RouteObservationEvent(
				TravelMode.PUBLIC_TRANSIT,
				RouteObservationOutcome.PROVIDER_VERIFIED,
				Set.of()
		));
		assertThat(publicTransitClient.receivedSegments())
				.containsExactly(routeSegment, routeSegment);
		verify(quotaService).reserve(12L, TravelMode.PUBLIC_TRANSIT, List.of(routeSegment));
		verify(quotaService).reserve(12L, TravelMode.PUBLIC_TRANSIT, 1L);
	}

	@Test
	void retryQuotaDenialSkipsTheSecondCallAndFallsBackTheWholeCandidate() {
		RouteSegment first = segment(37.5, 127.0, 37.6, 127.1);
		RouteSegment second = segment(37.6, 127.1, 37.7, 127.2);
		List<RouteSegment> segments = List.of(first, second);
		when(quotaService.reserve(11L, TravelMode.PUBLIC_TRANSIT, segments))
				.thenReturn(acquired());
		when(quotaService.reserve(11L, TravelMode.PUBLIC_TRANSIT, 1L))
				.thenReturn(denied(19L, UsageDenialScope.USER, UsageDenialScope.SERVICE));
		publicTransitClient.willFailThenReturn(RouteClientFailure.CONNECTION_FAILED, RouteResult.found(600));

		RouteVerificationResult result = service.verify(11L, TravelMode.PUBLIC_TRANSIT, segments);

		assertThat(result.travelTimes().segmentTravelTimes()).containsExactly(
				fallbackTime(TravelMode.PUBLIC_TRANSIT, first),
				fallbackTime(TravelMode.PUBLIC_TRANSIT, second)
		);
		assertThat(result.travelTimes().fallbackReason())
				.isEqualTo(RouteFallbackReason.QUOTA_UNAVAILABLE);
		assertThat(result.warnings()).containsExactly(RouteWarning.ESTIMATED_TRAVEL_TIMES_USED);
		assertThat(result.observationEvent()).isEqualTo(new RouteObservationEvent(
				TravelMode.PUBLIC_TRANSIT,
				RouteObservationOutcome.QUOTA_FALLBACK,
				Set.of(UsageDenialScope.USER, UsageDenialScope.SERVICE)
		));
		assertThat(publicTransitClient.receivedSegments()).containsExactly(first);
		assertThat(carClient.callCount()).isZero();
		verify(quotaService).reserve(11L, TravelMode.PUBLIC_TRANSIT, segments);
		verify(quotaService).reserve(11L, TravelMode.PUBLIC_TRANSIT, 1L);
		verify(quotaService).release(11L, TravelMode.PUBLIC_TRANSIT, acquired(), 1L);
	}

	@Test
	void oversizedDurationReleasesUnusedOriginalReservation() {
		RouteSegment first = segment(0.0, 0.0, 1.0, 1.0);
		RouteSegment second = segment(1.0, 1.0, 2.0, 2.0);
		List<RouteSegment> segments = List.of(first, second);
		UsageReservationLease reservation = acquired();
		when(quotaService.reserve(11L, TravelMode.CAR, segments)).thenReturn(reservation);
		carClient.willReturn(RouteResult.found(Long.MAX_VALUE));

		assertThatThrownBy(() -> service.verify(11L, TravelMode.CAR, segments))
				.isInstanceOfSatisfying(RouteClientException.class,
						exception -> assertThat(exception.failure())
								.isEqualTo(RouteClientFailure.INVALID_RESPONSE));
		assertThat(carClient.receivedSegments()).containsExactly(first);
		verify(quotaService).reserve(11L, TravelMode.CAR, segments);
		verify(quotaService).release(11L, TravelMode.CAR, reservation, 1L);
		verifyNoMoreInteractions(quotaService);
	}

	@Test
	void rejectsInvalidInputBeforeRequestingQuota() {
		List<RouteSegment> segmentWithNull = new ArrayList<>();
		segmentWithNull.add(null);

		assertThatNullPointerException()
				.isThrownBy(() -> new RouteVerificationService(null, mock(RouteService.class)));
		assertThatNullPointerException()
				.isThrownBy(() -> new RouteVerificationService(quotaService, null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.verify(1L, null, List.of()));
		assertThatNullPointerException()
				.isThrownBy(() -> service.verify(1L, TravelMode.CAR, null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.verify(1L, TravelMode.CAR, segmentWithNull));
		verifyNoMoreInteractions(quotaService);
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

	private UsageReservationLease acquired() {
		return UsageReservationLease.acquired(Instant.parse("2026-09-16T00:00:00Z"));
	}

	private UsageReservationLease denied(long retryAfterSeconds, UsageDenialScope... scopes) {
		return UsageReservationLease.denied(UsageReservationResult.denied(
				retryAfterSeconds,
				Set.of(scopes)
		));
	}
}
