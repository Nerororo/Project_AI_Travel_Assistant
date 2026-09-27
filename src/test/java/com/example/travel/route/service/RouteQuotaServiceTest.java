package com.example.travel.route.service;

import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.route.client.RouteSegment;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.dto.UsageDenialScope;
import com.example.travel.user.dto.UsageReservationResult;
import com.example.travel.user.dto.UsageReservationLease;
import com.example.travel.user.service.ApiUsageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RouteQuotaServiceTest {

	private final ApiUsageService apiUsageService = mock(ApiUsageService.class);
	private final RouteQuotaService service = new RouteQuotaService(apiUsageService);

	@Test
	void reservesOneCarCallForEachOrderedAdjacentSegment() {
		List<RouteSegment> segments = List.of(segment(0.0, 0.0, 1.0, 1.0), segment(1.0, 1.0, 2.0, 2.0));
		UsageReservationResult expected = UsageReservationResult.success();
		when(apiUsageService.tryAcquire(7L, UsageFeature.CAR_ROUTE, 2L)).thenReturn(expected);

		UsageReservationResult actual = service.tryAcquire(7L, TravelMode.CAR, segments);

		assertThat(actual).isSameAs(expected);
		verify(apiUsageService).tryAcquire(7L, UsageFeature.CAR_ROUTE, 2L);
	}

	@Test
	void keepsPublicTransitQuotaSeparateAndReturnsDenial() {
		UsageReservationResult expected = UsageReservationResult.denied(
				41L,
				Set.of(UsageDenialScope.SERVICE)
		);
		when(apiUsageService.tryAcquire(8L, UsageFeature.PUBLIC_TRANSIT_ROUTE, 1L)).thenReturn(expected);

		UsageReservationResult actual = service.tryAcquire(
				8L,
				TravelMode.PUBLIC_TRANSIT,
				List.of(segment(0.0, 0.0, 1.0, 1.0))
		);

		assertThat(actual).isSameAs(expected);
		verify(apiUsageService).tryAcquire(8L, UsageFeature.PUBLIC_TRANSIT_ROUTE, 1L);
	}

	@Test
	void acceptsAnEmptyCandidateWithoutReservingQuota() {
		UsageReservationResult result = service.tryAcquire(9L, TravelMode.CAR, List.of());

		assertThat(result.acquired()).isTrue();
		verifyNoInteractions(apiUsageService);
	}

	@Test
	void reservesOneAdditionalPublicTransitRetry() {
		UsageReservationResult expected = UsageReservationResult.success();
		when(apiUsageService.tryAcquire(10L, UsageFeature.PUBLIC_TRANSIT_ROUTE, 1L))
				.thenReturn(expected);

		UsageReservationResult actual = service.tryAcquire(10L, TravelMode.PUBLIC_TRANSIT, 1L);

		assertThat(actual).isSameAs(expected);
		verify(apiUsageService).tryAcquire(10L, UsageFeature.PUBLIC_TRANSIT_ROUTE, 1L);
	}

	@Test
	void releasesOnlyTheUnusedCarReservations() {
		UsageReservationLease reservation = UsageReservationLease.acquired(
				Instant.parse("2026-09-16T00:00:00Z")
		);
		service.release(10L, TravelMode.CAR, reservation, 2L);

		verify(apiUsageService).release(10L, UsageFeature.CAR_ROUTE, reservation, 2L);
	}

	@Test
	void rejectsInvalidArgumentsBeforeReservingQuota() {
		List<RouteSegment> segmentWithNull = new ArrayList<>();
		segmentWithNull.add(null);

		assertThatNullPointerException()
				.isThrownBy(() -> new RouteQuotaService(null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.tryAcquire(1L, null, List.of()));
		assertThatNullPointerException()
				.isThrownBy(() -> service.tryAcquire(1L, TravelMode.CAR, null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.tryAcquire(1L, TravelMode.CAR, segmentWithNull));
		assertThatNullPointerException()
				.isThrownBy(() -> service.tryAcquire(1L, null, 1L));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> service.tryAcquire(1L, TravelMode.CAR, -1L));
		assertThatNullPointerException()
				.isThrownBy(() -> service.release(1L, null,
						UsageReservationLease.acquired(Instant.EPOCH), 1L));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> service.release(1L, TravelMode.CAR,
						UsageReservationLease.acquired(Instant.EPOCH), 0L));
		verifyNoInteractions(apiUsageService);
	}

	private RouteSegment segment(double originLatitude, double originLongitude,
			double destinationLatitude, double destinationLongitude) {
		return new RouteSegment(
				new RouteSegment.Endpoint(originLatitude, originLongitude),
				new RouteSegment.Endpoint(destinationLatitude, destinationLongitude)
		);
	}
}
