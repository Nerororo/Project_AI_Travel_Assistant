package com.example.travel.route.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RouteClientContractTest {

	@Test
	void keepsCarAndPublicTransitClientsAsSeparateTypes() {
		RouteResult carResult = RouteResult.found(601);
		RouteResult transitResult = RouteResult.found(1_200);
		CarRouteClient carClient = segment -> carResult;
		PublicTransitRouteClient transitClient = segment -> transitResult;

		assertThat(carClient.findRoute(segment())).isSameAs(carResult);
		assertThat(transitClient.findRoute(segment())).isSameAs(transitResult);
		assertThat(carClient).isInstanceOf(RouteClient.class);
		assertThat(transitClient).isInstanceOf(RouteClient.class);
	}

	@Test
	void preservesTheOrderOfOneAdjacentSegment() {
		RouteSegment.Endpoint origin = new RouteSegment.Endpoint(0.0, 0.0);
		RouteSegment.Endpoint destination = new RouteSegment.Endpoint(1.0, 1.0);

		RouteSegment segment = new RouteSegment(origin, destination);

		assertThat(segment.origin()).isSameAs(origin);
		assertThat(segment.destination()).isSameAs(destination);
	}

	@Test
	void validatesRequestScopedCoordinates() {
		assertThatNullPointerException()
				.isThrownBy(() -> new RouteSegment(null, new RouteSegment.Endpoint(0.0, 0.0)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new RouteSegment.Endpoint(Double.NaN, 0.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new RouteSegment.Endpoint(90.000001, 0.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new RouteSegment.Endpoint(0.0, -180.000001));
	}

	@Test
	void representsAFoundRouteWithOnlyTheProviderDuration() {
		RouteResult result = RouteResult.found(601);

		assertThat(result).isEqualTo(new RouteResult.Found(601));
	}

	@Test
	void allowsZeroDurationAndRejectsNegativeDuration() {
		assertThat(RouteResult.found(0)).isEqualTo(new RouteResult.Found(0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RouteResult.found(-1));
	}

	@Test
	void representsNormalRouteAbsenceWithoutAnException() {
		RouteResult result = RouteResult.notFound();

		assertThat(result).isInstanceOf(RouteResult.NotFound.class);
	}

	@Test
	void distinguishesTransientTechnicalFailuresFromNonRetryableFailures() {
		assertThat(RouteClientFailure.TIMEOUT.isTransientTechnicalFailure()).isTrue();
		assertThat(RouteClientFailure.CONNECTION_FAILED.isTransientTechnicalFailure()).isTrue();
		assertThat(RouteClientFailure.PROVIDER_UNAVAILABLE.isTransientTechnicalFailure()).isTrue();
		assertThat(RouteClientFailure.INVALID_REQUEST.isTransientTechnicalFailure()).isFalse();
		assertThat(RouteClientFailure.INVALID_RESPONSE.isTransientTechnicalFailure()).isFalse();
	}

	@Test
	void exposesOnlyNormalizedFailureInformation() {
		RouteClientException exception = new RouteClientException(RouteClientFailure.TIMEOUT);

		assertThat(exception.failure()).isEqualTo(RouteClientFailure.TIMEOUT);
		assertThat(exception.getMessage()).isEqualTo("TIMEOUT");
		assertThat(exception.getCause()).isNull();
		assertThat(exception.getSuppressed()).isEmpty();
	}

	private RouteSegment segment() {
		return new RouteSegment(
				new RouteSegment.Endpoint(0.0, 0.0),
				new RouteSegment.Endpoint(1.0, 1.0)
		);
	}
}
