package com.example.travel.route.algorithm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class SevenDayRouteBenchmarkTest {

	private static final int REPETITIONS = 1_000;
	private static final RoutePoint ARRIVAL_STATION = point("arrival-station", 37.5547, 126.9707);
	private static final RoutePoint DEPARTURE_STATION = point("departure-station", 37.5299, 126.9648);

	@Test
	void evaluatesSevenDayRouteAgainstExactDistanceOptimum() {
		List<DayInput> days = sevenDays();
		double totalCalculatedKilometers = 0.0;
		double totalOptimalKilometers = 0.0;
		int totalTravelMinutes = 0;

		for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
			DayInput day = days.get(dayIndex);
			DayResult result = calculate(day);
			double exactOptimum = exactShortestLength(day);
			double gapPercent = percentageGap(result.lengthKilometers(), exactOptimum);

			assertThat(result.route()).hasSize(8);
			assertThat(result.route().getFirst()).isSameAs(day.fixedStart());
			assertThat(result.route().getLast()).isSameAs(day.fixedEnd());
			assertThat(result.route().subList(1, 7))
					.containsExactlyInAnyOrderElementsOf(day.places());
			assertThat(result.route().subList(1, 7).stream()
					.filter(place -> place.stableKey().contains("attraction")))
					.hasSize(4);
			assertThat(result.route().subList(1, 7).stream()
					.filter(place -> place.stableKey().contains("restaurant")))
					.hasSize(2);
			assertThat(result.lengthKilometers()).isLessThanOrEqualTo(result.initialLengthKilometers());
			assertThat(result.lengthKilometers()).isGreaterThanOrEqualTo(exactOptimum - 1.0e-9);

			totalCalculatedKilometers += result.lengthKilometers();
			totalOptimalKilometers += exactOptimum;
			totalTravelMinutes += result.travelMinutes();
			System.out.printf(
					"SEVEN_DAY_ROUTE day=%d initialKm=%.6f optimizedKm=%.6f exactKm=%.6f gapPercent=%.2f travelMinutes=%d%n",
					dayIndex + 1,
					result.initialLengthKilometers(),
					result.lengthKilometers(),
					exactOptimum,
					gapPercent,
					result.travelMinutes());
		}

		System.out.printf(
				"SEVEN_DAY_TOTAL optimizedKm=%.6f exactKm=%.6f gapPercent=%.2f travelMinutes=%d%n",
				totalCalculatedKilometers,
				totalOptimalKilometers,
				percentageGap(totalCalculatedKilometers, totalOptimalKilometers),
				totalTravelMinutes);
	}

	@Test
	void measuresCompleteSevenDayPureRouteCalculation() {
		List<DayInput> days = sevenDays();
		for (int warmup = 0; warmup < 100; warmup++) {
			calculateAll(days);
		}

		long startedAt = System.nanoTime();
		long checksum = assertTimeout(Duration.ofSeconds(5), () -> {
			long value = 0L;
			for (int repetition = 0; repetition < REPETITIONS; repetition++) {
				for (DayResult result : calculateAll(days)) {
					value += result.route().hashCode();
					value += result.travelMinutes();
				}
			}
			return value;
		});
		long elapsedNanoseconds = System.nanoTime() - startedAt;

		assertThat(checksum).isNotZero();
		System.out.printf(
				"SEVEN_DAY_PERFORMANCE repetitions=%d elapsedNs=%d averageSevenDayNs=%d averageDayNs=%d%n",
				REPETITIONS,
				elapsedNanoseconds,
				elapsedNanoseconds / REPETITIONS,
				elapsedNanoseconds / REPETITIONS / 7);
	}

	private static List<DayResult> calculateAll(List<DayInput> days) {
		return days.stream().map(SevenDayRouteBenchmarkTest::calculate).toList();
	}

	private static DayResult calculate(DayInput day) {
		List<RoutePoint> nearestNeighborInput = new ArrayList<>(day.places());
		nearestNeighborInput.add(day.fixedStart());
		List<RoutePoint> initial = new ArrayList<>(NearestNeighborRoute.order(
				nearestNeighborInput, Optional.of(day.fixedStart().stableKey())));
		initial.add(day.fixedEnd());
		List<RoutePoint> optimized = TwoOptRoute.improve(initial);
		HaversineTravelTimeEstimator estimator = new HaversineTravelTimeEstimator(
				TravelTimePolicy.defaultFor(TravelMode.CAR));
		int travelMinutes = 0;
		for (int index = 1; index < optimized.size(); index++) {
			travelMinutes += estimator.estimateMinutes(
					optimized.get(index - 1).coordinate(), optimized.get(index).coordinate());
		}
		return new DayResult(optimized, length(initial), length(optimized), travelMinutes);
	}

	private static double exactShortestLength(DayInput day) {
		List<RoutePoint> remaining = new ArrayList<>(day.places());
		return exactShortestLength(day.fixedStart(), day.fixedEnd(), remaining, 0.0, Double.POSITIVE_INFINITY);
	}

	private static double exactShortestLength(
			RoutePoint current,
			RoutePoint fixedEnd,
			List<RoutePoint> remaining,
			double distance,
			double best
	) {
		if (remaining.isEmpty()) {
			return Math.min(best, distance + distance(current, fixedEnd));
		}
		for (int index = 0; index < remaining.size(); index++) {
			RoutePoint next = remaining.remove(index);
			double nextDistance = distance + distance(current, next);
			if (nextDistance < best) {
				best = exactShortestLength(next, fixedEnd, remaining, nextDistance, best);
			}
			remaining.add(index, next);
		}
		return best;
	}

	private static double percentageGap(double actual, double optimum) {
		return (actual - optimum) / optimum * 100.0;
	}

	private static double length(List<RoutePoint> route) {
		double total = 0.0;
		for (int index = 1; index < route.size(); index++) {
			total += distance(route.get(index - 1), route.get(index));
		}
		return total;
	}

	private static double distance(RoutePoint from, RoutePoint to) {
		return HaversineDistance.kilometers(from.coordinate(), to.coordinate());
	}

	private static List<DayInput> sevenDays() {
		return List.of(
				day(ARRIVAL_STATION, hotelBoundary(1, "end"), 1,
						location(1, "attraction", 1, 37.5796, 126.9770),
						location(1, "attraction", 2, 37.5826, 126.9830),
						location(1, "attraction", 3, 37.5700, 126.9830),
						location(1, "attraction", 4, 37.5658, 126.9751),
						location(1, "restaurant", 1, 37.5712, 126.9802),
						location(1, "restaurant", 2, 37.5775, 126.9850)),
				day(hotelBoundary(2, "start"), hotelBoundary(2, "end"), 2,
						location(2, "attraction", 1, 37.5512, 126.9882),
						location(2, "attraction", 2, 37.5444, 127.0374),
						location(2, "attraction", 3, 37.5239, 126.9803),
						location(2, "attraction", 4, 37.5112, 127.0980),
						location(2, "restaurant", 1, 37.5365, 127.0000),
						location(2, "restaurant", 2, 37.5200, 127.0300)),
				day(hotelBoundary(3, "start"), hotelBoundary(3, "end"), 3,
						location(3, "attraction", 1, 37.5799, 126.9949),
						location(3, "attraction", 2, 37.5837, 127.0018),
						location(3, "attraction", 3, 37.5970, 127.0276),
						location(3, "attraction", 4, 37.5926, 127.0165),
						location(3, "restaurant", 1, 37.5810, 127.0060),
						location(3, "restaurant", 2, 37.5880, 127.0200)),
				day(hotelBoundary(4, "start"), hotelBoundary(4, "end"), 4,
						location(4, "attraction", 1, 37.5663, 126.9019),
						location(4, "attraction", 2, 37.5638, 126.9230),
						location(4, "attraction", 3, 37.5509, 126.9145),
						location(4, "attraction", 4, 37.5563, 126.9220),
						location(4, "restaurant", 1, 37.5590, 126.9240),
						location(4, "restaurant", 2, 37.5480, 126.9200)),
				day(hotelBoundary(5, "start"), hotelBoundary(5, "end"), 5,
						location(5, "attraction", 1, 37.5133, 127.1028),
						location(5, "attraction", 2, 37.5125, 127.0588),
						location(5, "attraction", 3, 37.5219, 127.0411),
						location(5, "attraction", 4, 37.5088, 127.0632),
						location(5, "restaurant", 1, 37.5140, 127.0800),
						location(5, "restaurant", 2, 37.5200, 127.0500)),
				day(hotelBoundary(6, "start"), hotelBoundary(6, "end"), 6,
						location(6, "attraction", 1, 37.4602, 126.4407),
						location(6, "attraction", 2, 37.4486, 126.4510),
						location(6, "attraction", 3, 37.4692, 126.4335),
						location(6, "attraction", 4, 37.4560, 126.4480),
						location(6, "restaurant", 1, 37.4630, 126.4400),
						location(6, "restaurant", 2, 37.4500, 126.4550)),
				day(hotelBoundary(7, "start"), DEPARTURE_STATION, 7,
						location(7, "attraction", 1, 37.5512, 126.9882),
						location(7, "attraction", 2, 37.5393, 126.9940),
						location(7, "attraction", 3, 37.5340, 126.9948),
						location(7, "attraction", 4, 37.5240, 126.9800),
						location(7, "restaurant", 1, 37.5450, 126.9900),
						location(7, "restaurant", 2, 37.5300, 126.9850)));
	}

	private static DayInput day(RoutePoint start, RoutePoint end, int day, RoutePoint... places) {
		return new DayInput(day, start, List.of(places), end);
	}

	private static RoutePoint hotelBoundary(int day, String boundary) {
		return point("day-" + day + "-hotel-" + boundary, 37.5663, 126.9820);
	}

	private static RoutePoint location(int day, String type, int sequence, double latitude, double longitude) {
		return point("day-" + day + "-" + type + "-" + sequence, latitude, longitude);
	}

	private static RoutePoint point(String key, double latitude, double longitude) {
		return new RoutePoint(key, new Coordinate(latitude, longitude));
	}

	private record DayInput(int day, RoutePoint fixedStart, List<RoutePoint> places, RoutePoint fixedEnd) {
	}

	private record DayResult(
			List<RoutePoint> route,
			double initialLengthKilometers,
			double lengthKilometers,
			int travelMinutes
	) {
	}
}
