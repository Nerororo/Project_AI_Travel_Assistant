package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.client.KakaoPlaceClient;
import com.example.travel.place.client.PlaceCandidate;
import com.example.travel.place.client.PlaceClientException;
import com.example.travel.place.client.PlaceSearchRequest;
import com.example.travel.place.client.PlaceSearchResult;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.domain.PlaceSearchRadiusPolicy;
import com.example.travel.place.dto.PlaceSearchRegionCriteria;
import com.example.travel.place.dto.RestaurantPlaceSearchRequest;
import com.example.travel.place.dto.RestaurantPlaceSearchResult;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Searches and validates temporary restaurant candidates, without ranking them. */
@Service
public class RestaurantSearchService {
	private final KakaoPlaceClient client;
	private final PlaceSearchRegionValidator regionValidator;
	private final PlaceSearchRegionService regionService;
	private final SelectionTokenService tokenService;
	private final RequestExecutionService executionService;
	private final ApiUsageService usageService;

	public RestaurantSearchService(KakaoPlaceClient client, PlaceSearchRegionValidator regionValidator,
			PlaceSearchRegionService regionService, SelectionTokenService tokenService,
			RequestExecutionService executionService, ApiUsageService usageService) {
		this.client = client;
		this.regionValidator = regionValidator;
		this.regionService = regionService;
		this.tokenService = tokenService;
		this.executionService = executionService;
		this.usageService = usageService;
	}

	public RestaurantPlaceSearchResult search(long userId, UUID requestId,
			RestaurantPlaceSearchRequest request) {
		validate(userId, requestId, request);
		regionValidator.validate(new PlaceSearchRegionCriteria(request.regionId(), null, PlaceRole.RESTAURANT));
		PlaceSearchRegion region = regionService.findById(request.regionId())
				.orElseThrow(RestaurantSearchService::validationFailed);
		PlaceSearchRequest.SearchBounds bounds = toBounds(request.bounds());
		RequestStartResult start = executionService.tryStart(userId, UsageFeature.PLACE_SEARCH, requestId, 1);
		if (!start.started()) {
			throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), start.retryAfterSeconds());
		}
		RequestExecutionLease lease = start.lease();
		try {
			RestaurantPlaceSearchResult response = searchCandidates(userId, request, region, bounds);
			if (!executionService.markSucceeded(lease)) {
				throw new IllegalStateException("Request execution could not be completed");
			}
			return response;
		} catch (PlaceClientException exception) {
			executionService.releaseAfterFailure(lease);
			throw new ApiException(ErrorCode.PLACE_PROVIDER_UNAVAILABLE);
		} catch (RuntimeException exception) {
			executionService.releaseAfterFailure(lease);
			throw exception;
		}
	}

	private RestaurantPlaceSearchResult searchCandidates(long userId,
			RestaurantPlaceSearchRequest request, PlaceSearchRegion region,
			PlaceSearchRequest.SearchBounds bounds) {
		if (bounds != null) {
			PlaceSearchResult result = client.search(PlaceSearchRequest.withinBounds(request.query(),
					bounds, request.page(), request.size()), retryCharge(userId));
			return toResult(result, userId, region);
		}
		int call = 0;
		RestaurantPlaceSearchResult response = null;
		for (int radius : PlaceSearchRadiusPolicy.radiiMeters(PlaceRole.RESTAURANT)) {
			if (call++ > 0) {
				usageService.acquireOrThrow(userId, UsageFeature.PLACE_SEARCH, 1);
			}
			PlaceSearchResult result = client.search(PlaceSearchRequest.around(request.query(),
					new PlaceSearchRequest.SearchCenter(request.center().latitude(),
							request.center().longitude()), radius, request.page(), request.size()),
					retryCharge(userId));
			response = toResult(result, userId, region);
			if (!response.places().isEmpty()) {
				break;
			}
		}
		return response;
	}

	private Runnable retryCharge(long userId) {
		return () -> usageService.acquireOrThrow(userId, UsageFeature.PLACE_SEARCH, 1);
	}

	private RestaurantPlaceSearchResult toResult(PlaceSearchResult result, long userId,
			PlaceSearchRegion region) {
		Map<String, PlaceCandidate> accepted = new LinkedHashMap<>();
		for (PlaceCandidate candidate : result.places()) {
			if (matches(candidate.address(), region)) {
				accepted.putIfAbsent(candidate.kakaoPlaceId(), candidate);
			}
		}
		List<RestaurantPlaceSearchResult.Place> places = accepted.values().stream().map(candidate -> {
			String token = tokenService.issue(new SelectionTokenPlace(userId, region.regionId(),
					PlaceRole.RESTAURANT, candidate.kakaoPlaceId(), candidate.placeUrl(),
					candidate.latitude(), candidate.longitude()));
			return new RestaurantPlaceSearchResult.Place(candidate.kakaoPlaceId(), candidate.placeUrl(),
					candidate.providerDisplayName(), candidate.address(),
					new Coordinate(candidate.latitude(), candidate.longitude()), token);
		}).toList();
		return new RestaurantPlaceSearchResult(places, result.page(), result.hasNext());
	}

	private static boolean matches(String address, PlaceSearchRegion region) {
		PlaceSearchRegion.AddressBoundary boundary = region.addressBoundary();
		if (boundary == null || address == null) {
			return false;
		}
		String[] parts = address.trim().split("\\s+");
		return parts.length > 0 && boundary.region1Names().contains(parts[0])
				&& (boundary.region2Names().isEmpty()
				|| (parts.length > 1 && boundary.region2Names().contains(parts[1])));
	}

	private static PlaceSearchRequest.SearchBounds toBounds(RestaurantPlaceSearchRequest.Bounds bounds) {
		if (bounds == null) return null;
		try {
			return new PlaceSearchRequest.SearchBounds(bounds.minLatitude(), bounds.minLongitude(),
					bounds.maxLatitude(), bounds.maxLongitude());
		} catch (IllegalArgumentException exception) {
			throw validationFailed();
		}
	}

	private static void validate(long userId, UUID requestId, RestaurantPlaceSearchRequest request) {
		if (userId <= 0 || requestId == null || request == null || request.regionId() == null
				|| request.regionId().isBlank() || request.query() == null
				|| request.query().isBlank() || request.query().trim().length() > 50
				|| (request.center() == null) == (request.bounds() == null)
				|| request.page() < 1 || request.page() > 45
				|| request.size() < 1 || request.size() > 15) {
			throw validationFailed();
		}
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}
}
