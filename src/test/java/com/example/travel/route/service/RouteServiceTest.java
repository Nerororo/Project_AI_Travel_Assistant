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
import org.junit.jupiter.params.provider.EnumSource;

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
		return new RouteSegment(
				new RouteSegment.Endpoint(0.0, 0.0),
				new RouteSegment.Endpoint(1.0, 1.0)
		);
	}
}
