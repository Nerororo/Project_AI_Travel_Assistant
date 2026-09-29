package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.client.KakaoPlaceClient;
import com.example.travel.place.client.PlaceCandidate;
import com.example.travel.place.client.PlaceClientException;
import com.example.travel.place.client.PlaceSearchRequest;
import com.example.travel.place.client.PlaceSearchResult;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.HotelSearchApiRequest;
import com.example.travel.place.dto.HotelSearchApiResponse;
import com.example.travel.place.dto.PlaceSearchRegionCriteria;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.route.algorithm.GeometricMedian;
import com.example.travel.route.algorithm.MedoidSelector;
import com.example.travel.route.algorithm.RoutePoint;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Searches hotels from verified, request-scoped attraction coordinates. */
@Service
public class HotelSearchService {
	private static final String HOTEL_QUERY = "숙소";
	private static final int DEFAULT_RADIUS_METERS = 5_000;

	private final KakaoPlaceClient kakaoPlaceClient;
	private final PlaceSearchRegionValidator regionValidator;
	private final PlaceSearchRegionService regionService;
	private final PlanningPlaceSelectionService planningSelectionService;
	private final SelectionTokenService selectionTokenService;
	private final RequestExecutionService requestExecutionService;
	private final ApiUsageService apiUsageService;

	public HotelSearchService(KakaoPlaceClient kakaoPlaceClient,
			PlaceSearchRegionValidator regionValidator,
			PlaceSearchRegionService regionService,
			PlanningPlaceSelectionService planningSelectionService,
			SelectionTokenService selectionTokenService,
			RequestExecutionService requestExecutionService,
			ApiUsageService apiUsageService) {
		this.kakaoPlaceClient = kakaoPlaceClient;
		this.regionValidator = regionValidator;
		this.regionService = regionService;
		this.planningSelectionService = planningSelectionService;
		this.selectionTokenService = selectionTokenService;
		this.requestExecutionService = requestExecutionService;
		this.apiUsageService = apiUsageService;
	}

	public HotelSearchApiResponse search(long userId, UUID requestId, HotelSearchApiRequest request) {
		validateRequest(userId, requestId, request);
		regionValidator.validate(new PlaceSearchRegionCriteria(request.regionId(), null, PlaceRole.HOTEL));
		PlaceSearchRegion region = regionService.findById(request.regionId())
				.orElseThrow(HotelSearchService::validationFailed);
		List<RoutePoint> attractions = verifyAttractions(userId, request);
		PlaceSearchRequest clientRequest = toClientRequest(request, attractions);

		RequestStartResult start = requestExecutionService.tryStart(
				userId, UsageFeature.PLACE_SEARCH, requestId, 1);
		if (!start.started()) {
			throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), start.retryAfterSeconds());
		}
		RequestExecutionLease lease = start.lease();
		try {
			PlaceSearchResult result = kakaoPlaceClient.search(clientRequest,
					() -> apiUsageService.acquireOrThrow(userId, UsageFeature.PLACE_SEARCH, 1));
			HotelSearchApiResponse response = toResponse(result, userId, region);
			if (!requestExecutionService.markSucceeded(lease)) {
				throw new IllegalStateException("Request execution could not be completed");
			}
			return response;
		} catch (PlaceClientException exception) {
			requestExecutionService.releaseAfterFailure(lease);
			throw new ApiException(ErrorCode.PLACE_PROVIDER_UNAVAILABLE);
		} catch (RuntimeException exception) {
			requestExecutionService.releaseAfterFailure(lease);
			throw exception;
		}
	}

	private static void validateRequest(long userId, UUID requestId, HotelSearchApiRequest request) {
		if (userId <= 0 || requestId == null || request == null || request.regionId() == null
				|| request.regionId().isBlank() || request.attractionSelectionTokens() == null
				|| request.attractionSelectionTokens().isEmpty()
				|| request.attractionSelectionTokens().size() > 35 || request.mode() == null
				|| request.page() < 1 || request.page() > 45 || request.size() < 1 || request.size() > 15) {
			throw validationFailed();
		}
		for (String token : request.attractionSelectionTokens()) {
			if (token == null || token.isBlank()) {
				throw validationFailed();
			}
		}
		switch (request.mode()) {
			case GEOMETRIC_MEDIAN -> {
				if (request.bounds() != null || (request.radiusMeters() != null
						&& request.radiusMeters() != 5_000 && request.radiusMeters() != 10_000)) {
					throw validationFailed();
				}
			}
			case MEDOID -> {
				if (request.bounds() != null || request.radiusMeters() != null) {
					throw validationFailed();
				}
			}
			case MAP_BOUNDS -> {
				if (request.bounds() == null || request.radiusMeters() != null) {
					throw validationFailed();
				}
			}
		}
	}

	private List<RoutePoint> verifyAttractions(long userId, HotelSearchApiRequest request) {
		List<RoutePoint> points = new ArrayList<>(request.attractionSelectionTokens().size());
		Set<String> ids = new HashSet<>();
		for (String token : request.attractionSelectionTokens()) {
			PlanningPlaceSelection place = planningSelectionService.verifyAttraction(
					token, userId, request.regionId());
			if (!ids.add(place.kakaoPlaceId())) {
				throw validationFailed();
			}
			points.add(new RoutePoint(place.kakaoPlaceId(),
					new Coordinate(place.latitude(), place.longitude())));
		}
		return points;
	}

	private PlaceSearchRequest toClientRequest(HotelSearchApiRequest request, List<RoutePoint> attractions) {
		try {
			if (request.mode() == HotelSearchApiRequest.Mode.MAP_BOUNDS) {
				HotelSearchApiRequest.Bounds bounds = request.bounds();
				return PlaceSearchRequest.withinBounds(HOTEL_QUERY,
						new PlaceSearchRequest.SearchBounds(bounds.minLatitude(), bounds.minLongitude(),
								bounds.maxLatitude(), bounds.maxLongitude()), request.page(), request.size());
			}
			Coordinate center = request.mode() == HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN
					? GeometricMedian.calculate(attractions.stream().map(RoutePoint::coordinate).toList())
					: MedoidSelector.select(attractions).coordinate();
			int radius = request.mode() == HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN
					&& request.radiusMeters() != null ? request.radiusMeters() : DEFAULT_RADIUS_METERS;
			return PlaceSearchRequest.around(HOTEL_QUERY,
					new PlaceSearchRequest.SearchCenter(center.latitude(), center.longitude()),
					radius, request.page(), request.size());
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw validationFailed();
		}
	}

	private HotelSearchApiResponse toResponse(PlaceSearchResult result, long userId,
			PlaceSearchRegion region) {
		Map<String, PlaceCandidate> accepted = new LinkedHashMap<>();
		for (PlaceCandidate candidate : result.places()) {
			if (matches(candidate.address(), region)) {
				accepted.putIfAbsent(candidate.kakaoPlaceId(), candidate);
			}
		}
		List<HotelSearchApiResponse.Place> places = accepted.values().stream().map(candidate -> {
			String token = selectionTokenService.issue(new SelectionTokenPlace(userId,
					region.regionId(), PlaceRole.HOTEL, candidate.kakaoPlaceId(), candidate.placeUrl(),
					candidate.latitude(), candidate.longitude()));
			return new HotelSearchApiResponse.Place(candidate.kakaoPlaceId(), candidate.placeUrl(),
					candidate.providerDisplayName(), candidate.address(), candidate.latitude(),
					candidate.longitude(), token);
		}).toList();
		return new HotelSearchApiResponse(places, result.page(), result.hasNext());
	}

	private static boolean matches(String address, PlaceSearchRegion region) {
		PlaceSearchRegion.AddressBoundary boundary = region.addressBoundary();
		if (boundary == null || address == null) {
			return false;
		}
		String[] parts = address.trim().split("\\s+");
		if (parts.length == 0 || !boundary.region1Names().contains(parts[0])) {
			return false;
		}
		return boundary.region2Names().isEmpty()
				|| (parts.length > 1 && boundary.region2Names().contains(parts[1]));
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}
}
