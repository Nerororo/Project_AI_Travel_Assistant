package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NearestNeighborRouteTest {

	@Test
	void returnsImmutableEmptyRouteForEmptyInputWithoutStart() {
		List<RoutePoint> route = NearestNeighborRoute.order(List.of(), Optional.empty());

		assertThat(route).isEmpty();
		assertThatThrownBy(() -> route.add(point("new", 0.0, 0.0)))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void returnsSinglePlaceWhenItIsTheStart() {
		RoutePoint only = point("only", 0.0, 0.0);

		assertThat(NearestNeighborRoute.order(List.of(only), Optional.of("only")))
				.containsExactly(only);
	}

	@Test
	void visitsEveryPlaceExactlyOnceFromTheFixedStart() {
		RoutePoint start = point("start", 0.0, 0.0);
		RoutePoint nearest = point("nearest", 0.0, 0.01);
		RoutePoint middle = point("middle", 0.0, 0.02);
		RoutePoint farthest = point("farthest", 0.0, 0.03);

		List<RoutePoint> route = NearestNeighborRoute.order(
				List.of(farthest, middle, start, nearest),
				Optional.of("start"));

		assertThat(route).containsExactly(start, nearest, middle, farthest);
		assertThat(route).doesNotHaveDuplicates();
	}

	@Test
	void resolvesEqualDistancesByStableKeyRegardlessOfInputOrder() {
		RoutePoint start = point("start", 0.0, 0.0);
		RoutePoint alpha = point("alpha", 0.0, 0.01);
		RoutePoint beta = point("beta", 0.0, -0.01);

		List<RoutePoint> first = NearestNeighborRoute.order(
				List.of(start, beta, alpha),
				Optional.of("start"));
		List<RoutePoint> second = NearestNeighborRoute.order(
				List.of(alpha, start, beta),
				Optional.of("start"));

		assertThat(first).containsExactly(start, alpha, beta);
		assertThat(second).containsExactlyElementsOf(first);
	}

	@Test
	void doesNotMutateInputAndReturnsImmutableRoute() {
		RoutePoint start = point("start", 0.0, 0.0);
		RoutePoint next = point("next", 0.0, 0.01);
		List<RoutePoint> input = new ArrayList<>(List.of(next, start));
		List<RoutePoint> original = List.copyOf(input);

		List<RoutePoint> route = NearestNeighborRoute.order(input, Optional.of("start"));

		assertThat(input).containsExactlyElementsOf(original);
		assertThatThrownBy(() -> route.add(point("new", 0.0, 0.02)))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsStartContractViolations() {
		RoutePoint place = point("place", 0.0, 0.0);

		assertThatIllegalArgumentException()
				.isThrownBy(() -> NearestNeighborRoute.order(List.of(), Optional.of("place")));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> NearestNeighborRoute.order(List.of(place), Optional.empty()));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> NearestNeighborRoute.order(List.of(place), Optional.of("missing")));
	}

	@Test
	void rejectsDuplicateKeysAndNullInputs() {
		RoutePoint first = point("duplicate", 0.0, 0.0);
		RoutePoint second = point("duplicate", 0.0, 0.01);

		assertThatIllegalArgumentException()
				.isThrownBy(() -> NearestNeighborRoute.order(
						List.of(first, second),
						Optional.of("duplicate")));
		assertThatNullPointerException()
				.isThrownBy(() -> NearestNeighborRoute.order(null, Optional.empty()));
		assertThatNullPointerException()
				.isThrownBy(() -> NearestNeighborRoute.order(List.of(), null));
		assertThatNullPointerException()
				.isThrownBy(() -> NearestNeighborRoute.order(
						Arrays.asList(first, null),
						Optional.of("duplicate")));
	}

	private static RoutePoint point(String key, double latitude, double longitude) {
		return new RoutePoint(key, new Coordinate(latitude, longitude));
	}
}
