package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwoOptRouteTest {

	@Test
	void improvesCrossingOpenRouteWithoutChangingBoundariesOrPlaces() {
		RoutePoint start = point("start", 0.0, 0.0);
		RoutePoint northEast = point("north-east", 1.0, 1.0);
		RoutePoint northWest = point("north-west", 1.0, 0.0);
		RoutePoint southEast = point("south-east", 0.0, 1.0);
		List<RoutePoint> input = List.of(start, northEast, northWest, southEast);

		List<RoutePoint> result = TwoOptRoute.improve(input);

		assertThat(result).containsExactly(start, northWest, northEast, southEast);
		assertThat(result.getFirst()).isSameAs(start);
		assertThat(result.getLast()).isSameAs(southEast);
		assertThat(result).containsExactlyInAnyOrderElementsOf(input);
		assertThat(length(result)).isLessThan(length(input));
	}

	@Test
	void leavesRouteUnchangedWhenNoImprovementExists() {
		List<RoutePoint> input = List.of(
				point("start", 0.0, 0.0),
				point("middle", 0.0, 1.0),
				point("end", 0.0, 2.0));

		assertThat(TwoOptRoute.improve(input)).containsExactlyElementsOf(input);
	}

	@Test
	void isDeterministicForTheSameOrderedRoute() {
		List<RoutePoint> input = List.of(
				point("start", 0.0, 0.0),
				point("north-east", 1.0, 1.0),
				point("north-west", 1.0, 0.0),
				point("south-east", 0.0, 1.0));

		List<RoutePoint> first = TwoOptRoute.improve(input);
		List<RoutePoint> second = TwoOptRoute.improve(input);

		assertThat(second).containsExactlyElementsOf(first);
	}

	@Test
	void stopsAtConfiguredIterationLimitWithoutWorseningRoute() {
		List<RoutePoint> input = List.of(
				point("start", 0.0, 0.0),
				point("north-east", 1.0, 1.0),
				point("north-west", 1.0, 0.0),
				point("south-east", 0.0, 1.0));

		List<RoutePoint> result = TwoOptRoute.improve(input, 1);

		assertThat(result.getFirst()).isEqualTo(input.getFirst());
		assertThat(result.getLast()).isEqualTo(input.getLast());
		assertThat(result).containsExactlyInAnyOrderElementsOf(input);
		assertThat(length(result)).isLessThanOrEqualTo(length(input));
	}

	@Test
	void returnsImmutableCopiesForEmptySingleAndMultiPointRoutes() {
		RoutePoint only = point("only", 0.0, 0.0);
		List<RoutePoint> empty = TwoOptRoute.improve(List.of());
		List<RoutePoint> single = TwoOptRoute.improve(List.of(only));
		List<RoutePoint> multi = TwoOptRoute.improve(List.of(
				only,
				point("next", 0.0, 1.0)));

		assertThat(empty).isEmpty();
		assertThat(single).containsExactly(only);
		assertThatThrownBy(() -> empty.add(only)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> single.add(only)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> multi.add(only)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void preservesDistinctHotelBoundariesAtTheSameCoordinate() {
		RoutePoint hotelStart = point("day-start", 37.5, 127.0);
		RoutePoint firstVisit = point("first-visit", 37.6, 127.1);
		RoutePoint secondVisit = point("second-visit", 37.6, 127.0);
		RoutePoint hotelEnd = point("day-end", 37.5, 127.0);

		List<RoutePoint> result = TwoOptRoute.improve(
				List.of(hotelStart, firstVisit, secondVisit, hotelEnd));

		assertThat(result.getFirst()).isSameAs(hotelStart);
		assertThat(result.getLast()).isSameAs(hotelEnd);
		assertThat(result).containsExactlyInAnyOrder(
				hotelStart, firstVisit, secondVisit, hotelEnd);
	}

	@Test
	void doesNotMutateInput() {
		List<RoutePoint> input = new ArrayList<>(List.of(
				point("start", 0.0, 0.0),
				point("north-east", 1.0, 1.0),
				point("north-west", 1.0, 0.0),
				point("south-east", 0.0, 1.0)));
		List<RoutePoint> original = List.copyOf(input);

		TwoOptRoute.improve(input);

		assertThat(input).containsExactlyElementsOf(original);
	}

	@Test
	void rejectsInvalidInputAndIterationLimit() {
		RoutePoint duplicateA = point("duplicate", 0.0, 0.0);
		RoutePoint duplicateB = point("duplicate", 0.0, 1.0);

		assertThatNullPointerException().isThrownBy(() -> TwoOptRoute.improve(null));
		assertThatNullPointerException()
				.isThrownBy(() -> TwoOptRoute.improve(Arrays.asList(duplicateA, null)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> TwoOptRoute.improve(List.of(duplicateA, duplicateB)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> TwoOptRoute.improve(List.of(duplicateA), 0));
	}

	private static double length(List<RoutePoint> route) {
		double total = 0.0;
		for (int index = 1; index < route.size(); index++) {
			total += HaversineDistance.kilometers(
					route.get(index - 1).coordinate(),
					route.get(index).coordinate());
		}
		return total;
	}

	private static RoutePoint point(String key, double latitude, double longitude) {
		return new RoutePoint(key, new Coordinate(latitude, longitude));
	}
}
