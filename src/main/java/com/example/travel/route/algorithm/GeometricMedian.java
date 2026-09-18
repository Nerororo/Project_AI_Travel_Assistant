package com.example.travel.route.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Calculates a geometric median in a local equirectangular kilometre plane.
 */
public final class GeometricMedian {

	private static final double CONVERGENCE_KILOMETERS = 1e-6;
	private static final double COINCIDENCE_KILOMETERS = 1e-9;
	private static final int MAX_ITERATIONS = 1_000;

	private GeometricMedian() {
	}

	public static Coordinate calculate(List<Coordinate> coordinates) {
		return calculate(coordinates, MAX_ITERATIONS);
	}

	static Coordinate calculate(List<Coordinate> coordinates, int maxIterations) {
		Objects.requireNonNull(coordinates, "coordinates must not be null");
		if (maxIterations <= 0) {
			throw new IllegalArgumentException("maxIterations must be positive");
		}

		List<Coordinate> input = new ArrayList<>(List.copyOf(coordinates));
		if (input.isEmpty()) {
			throw new IllegalArgumentException("coordinates must not be empty");
		}
		input.sort(Comparator.comparingDouble(Coordinate::latitude)
				.thenComparingDouble(Coordinate::longitude));
		if (input.size() == 1) {
			return input.getFirst();
		}

		Projection projection = Projection.from(input);
		List<PlanePoint> points = input.stream()
				.map(projection::project)
				.toList();
		PlanePoint current = arithmeticMean(points);

		for (int iteration = 0; iteration < maxIterations; iteration++) {
			PlanePoint next = nextPoint(current, points);
			double movement = current.distanceTo(next);
			current = next;
			if (movement <= CONVERGENCE_KILOMETERS) {
				break;
			}
		}

		return projection.unproject(current);
	}

	private static PlanePoint nextPoint(PlanePoint current, List<PlanePoint> points) {
		double weightedX = 0.0;
		double weightedY = 0.0;
		double weightSum = 0.0;
		double residualX = 0.0;
		double residualY = 0.0;
		int coincidentCount = 0;

		for (PlanePoint point : points) {
			double distance = current.distanceTo(point);
			if (distance <= COINCIDENCE_KILOMETERS) {
				coincidentCount++;
				continue;
			}
			double weight = 1.0 / distance;
			weightedX += point.x() * weight;
			weightedY += point.y() * weight;
			weightSum += weight;
			residualX += (point.x() - current.x()) * weight;
			residualY += (point.y() - current.y()) * weight;
		}

		if (weightSum == 0.0) {
			return current;
		}

		PlanePoint weightedMean = new PlanePoint(weightedX / weightSum, weightedY / weightSum);
		if (coincidentCount == 0) {
			return weightedMean;
		}

		double residualMagnitude = Math.hypot(residualX, residualY);
		if (residualMagnitude <= coincidentCount) {
			return current;
		}

		double retainedRatio = coincidentCount / residualMagnitude;
		return new PlanePoint(
				(1.0 - retainedRatio) * weightedMean.x() + retainedRatio * current.x(),
				(1.0 - retainedRatio) * weightedMean.y() + retainedRatio * current.y());
	}

	private static PlanePoint arithmeticMean(List<PlanePoint> points) {
		double x = 0.0;
		double y = 0.0;
		for (PlanePoint point : points) {
			x += point.x();
			y += point.y();
		}
		return new PlanePoint(x / points.size(), y / points.size());
	}

	private record PlanePoint(double x, double y) {

		double distanceTo(PlanePoint other) {
			return Math.hypot(x - other.x, y - other.y);
		}
	}

	private record Projection(double referenceLatitude, double referenceLongitude, double cosineLatitude) {

		static Projection from(List<Coordinate> coordinates) {
			double latitudeSum = 0.0;
			double longitudeSum = 0.0;
			for (Coordinate coordinate : coordinates) {
				latitudeSum += coordinate.latitude();
				longitudeSum += coordinate.longitude();
			}
			double referenceLatitude = latitudeSum / coordinates.size();
			double referenceLongitude = longitudeSum / coordinates.size();
			double cosineLatitude = Math.cos(Math.toRadians(referenceLatitude));
			if (Math.abs(cosineLatitude) < 1e-12) {
				throw new IllegalArgumentException("coordinates must support a local longitude projection");
			}
			return new Projection(referenceLatitude, referenceLongitude, cosineLatitude);
		}

		PlanePoint project(Coordinate coordinate) {
			double longitudeDelta = normalizeLongitude(coordinate.longitude() - referenceLongitude);
			double x = HaversineDistance.EARTH_RADIUS_KILOMETERS
					* Math.toRadians(longitudeDelta)
					* cosineLatitude;
			double y = HaversineDistance.EARTH_RADIUS_KILOMETERS
					* Math.toRadians(coordinate.latitude() - referenceLatitude);
			return new PlanePoint(x, y);
		}

		Coordinate unproject(PlanePoint point) {
			double latitude = referenceLatitude
					+ Math.toDegrees(point.y() / HaversineDistance.EARTH_RADIUS_KILOMETERS);
			double longitude = normalizeLongitude(referenceLongitude
					+ Math.toDegrees(point.x()
					/ (HaversineDistance.EARTH_RADIUS_KILOMETERS * cosineLatitude)));
			return new Coordinate(latitude, longitude);
		}

		private static double normalizeLongitude(double longitude) {
			double normalized = longitude % 360.0;
			if (normalized > 180.0) {
				normalized -= 360.0;
			}
			if (normalized < -180.0) {
				normalized += 360.0;
			}
			return normalized == 0.0 ? 0.0 : normalized;
		}
	}
}
