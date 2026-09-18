package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class RouteAlgorithmRegressionTest {

	private static final int PERFORMANCE_REPETITIONS = 1_000;
	private static final Duration PERFORMANCE_BUDGET = Duration.ofSeconds(5);

	@Test
	void composesMaximumMvpDayDeterministicallyWithoutChangingBoundariesOrPlaces() {
		RoutePoint fixedStart = point("day-start", 37.5665, 126.9780);
		List<RoutePoint> attractions = maximumAttractions();
		RoutePoint fixedEnd = point("day-end", 37.5665, 126.9780);

		RouteCalculation first = calculate(fixedStart, attractions, fixedEnd, TravelMode.CAR);
		RouteCalculation second = calculate(fixedStart, attractions.reversed(), fixedEnd, TravelMode.CAR);

		assertThat(first.route()).hasSize(7);
		assertThat(first.route().getFirst()).isSameAs(fixedStart);
		assertThat(first.route().getLast()).isSameAs(fixedEnd);
		assertThat(first.route().subList(1, 6))
				.containsExactlyInAnyOrderElementsOf(attractions);
		assertThat(first.route()).containsExactlyElementsOf(second.route());
		assertThat(first.routeLengthKilometers())
				.isLessThanOrEqualTo(first.initialRouteLengthKilometers());
		assertThat(first.segmentMinutes())
				.hasSize(6)
				.allSatisfy(minutes -> {
					assertThat(minutes).isPositive();
					assertThat(minutes % 10).isZero();
				});
		assertThat(first.geometricMedian()).isEqualTo(second.geometricMedian());
		assertThat(first.medoid()).isSameAs(second.medoid());
		assertThat(first.geometricMedian().latitude()).isFinite();
		assertThat(first.geometricMedian().longitude()).isFinite();
		assertThat(attractions).contains(first.medoid());
	}

	@Test
	void keepsCarAndPublicTransitCalculationsSeparateForEverySegment() {
		RoutePoint fixedStart = point("day-start", 37.5665, 126.9780);
		List<RoutePoint> attractions = maximumAttractions();
		RoutePoint fixedEnd = point("day-end", 37.5665, 126.9780);

		RouteCalculation car = calculate(fixedStart, attractions, fixedEnd, TravelMode.CAR);
		RouteCalculation publicTransit = calculate(
				fixedStart, attractions, fixedEnd, TravelMode.PUBLIC_TRANSIT);

		assertThat(car.route()).containsExactlyElementsOf(publicTransit.route());
		assertThat(car.segmentMinutes()).allSatisfy(minutes -> assertThat(minutes % 10).isZero());
		assertThat(publicTransit.segmentMinutes())
				.allSatisfy(minutes -> assertThat(minutes % 10).isZero());
		assertThat(publicTransit.segmentMinutes())
				.zipSatisfy(car.segmentMinutes(),
						(publicMinutes, carMinutes) -> assertThat(publicMinutes)
								.isGreaterThanOrEqualTo(carMinutes));
	}

	@Test
	void calculatesMaximumMvpInputRepeatedlyWithinRegressionBudget() {
		RoutePoint fixedStart = point("day-start", 37.5665, 126.9780);
		List<RoutePoint> attractions = maximumAttractions();
		RoutePoint fixedEnd = point("day-end", 37.5665, 126.9780);

		for (int warmup = 0; warmup < 100; warmup++) {
			calculate(fixedStart, attractions, fixedEnd, TravelMode.CAR);
		}

		long startedAt = System.nanoTime();
		long checksum = assertTimeout(PERFORMANCE_BUDGET, () -> {
			long value = 0L;
			for (int repetition = 0; repetition < PERFORMANCE_REPETITIONS; repetition++) {
				RouteCalculation result = calculate(
						fixedStart, attractions, fixedEnd, TravelMode.CAR);
				value += result.route().hashCode();
				value += result.segmentMinutes().stream().mapToInt(Integer::intValue).sum();
			}
			return value;
		});
		long elapsedNanoseconds = System.nanoTime() - startedAt;
		RouteCalculation representative = calculate(
				fixedStart, attractions, fixedEnd, TravelMode.CAR);
		double improvementPercent = (representative.initialRouteLengthKilometers()
				- representative.routeLengthKilometers())
				/ representative.initialRouteLengthKilometers() * 100.0;

		assertThat(checksum).isNotZero();
		System.out.printf(
				"R1_MAX_MVP repetitions=%d elapsedNs=%d averageNs=%d "
						+ "initialKm=%.6f optimizedKm=%.6f improvementPercent=%.2f%n",
				PERFORMANCE_REPETITIONS,
				elapsedNanoseconds,
				elapsedNanoseconds / PERFORMANCE_REPETITIONS,
				representative.initialRouteLengthKilometers(),
				representative.routeLengthKilometers(),
				improvementPercent);
	}

	private static RouteCalculation calculate(
			RoutePoint fixedStart,
			List<RoutePoint> attractions,
			RoutePoint fixedEnd,
			TravelMode travelMode
	) {
		List<RoutePoint> nearestNeighborInput = new ArrayList<>(attractions);
		nearestNeighborInput.add(fixedStart);
		List<RoutePoint> initialVisits = NearestNeighborRoute.order(
				nearestNeighborInput,
				Optional.of(fixedStart.stableKey()));

		List<RoutePoint> initialRoute = new ArrayList<>(initialVisits);
		initialRoute.add(fixedEnd);
		List<RoutePoint> improvedRoute = TwoOptRoute.improve(initialRoute);

		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(
				TravelTimePolicy.defaultFor(travelMode));
		List<Integer> segmentMinutes = new ArrayList<>();
		for (int index = 1; index < improvedRoute.size(); index++) {
			segmentMinutes.add(estimator.estimateMinutes(
					improvedRoute.get(index - 1).coordinate(),
					improvedRoute.get(index).coordinate()));
		}

		List<Coordinate> attractionCoordinates = attractions.stream()
				.map(RoutePoint::coordinate)
				.toList();
		return new RouteCalculation(
				improvedRoute,
				List.copyOf(segmentMinutes),
				GeometricMedian.calculate(attractionCoordinates),
				MedoidSelector.select(attractions),
				length(initialRoute),
				length(improvedRoute));
	}

	private static List<RoutePoint> maximumAttractions() {
		return List.of(
				point("palace", 37.5796, 126.9770),
				point("tower", 37.5512, 126.9882),
				point("forest", 37.5444, 127.0374),
				point("museum", 37.5239, 126.9803),
				point("village", 37.5826, 126.9830));
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

	private record RouteCalculation(
			List<RoutePoint> route,
			List<Integer> segmentMinutes,
			Coordinate geometricMedian,
			RoutePoint medoid,
			double initialRouteLengthKilometers,
			double routeLengthKilometers
	) {
	}
}
