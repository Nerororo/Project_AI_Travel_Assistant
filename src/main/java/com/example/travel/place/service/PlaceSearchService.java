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
import com.example.travel.place.domain.StayDurationPolicy;
import com.example.travel.place.dto.PlaceSearchApiRequest;
import com.example.travel.place.dto.PlaceSearchApiResponse;
import com.example.travel.place.dto.PlaceSearchRegionCriteria;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.RequestExecutionService;
import com.example.travel.user.service.ApiUsageService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

@Service
public class PlaceSearchService {

	private final KakaoPlaceClient kakaoPlaceClient;
	private final PlaceSearchRegionValidator regionValidator;
	private final PlaceSearchRegionService regionService;
	private final SelectionTokenService selectionTokenService;
	private final RequestExecutionService requestExecutionService;
	private final ApiUsageService apiUsageService;

	public PlaceSearchService(
			KakaoPlaceClient kakaoPlaceClient,
			PlaceSearchRegionValidator regionValidator,
			PlaceSearchRegionService regionService,
			SelectionTokenService selectionTokenService,
			RequestExecutionService requestExecutionService,
			ApiUsageService apiUsageService
	) {
		this.kakaoPlaceClient = kakaoPlaceClient;
		this.regionValidator = regionValidator;
		this.regionService = regionService;
		this.selectionTokenService = selectionTokenService;
		this.requestExecutionService = requestExecutionService;
		this.apiUsageService = apiUsageService;
	}

	public PlaceSearchApiResponse search(long userId, UUID requestId, PlaceSearchApiRequest request) {
		validateRequest(request);
		PlaceSearchRegionCriteria criteria = new PlaceSearchRegionCriteria(
				request.regionId(), request.districtFilterId(), request.placeRole());
		regionValidator.validate(criteria);
		PlaceSearchRegion travelRegion = requiredRegion(request.regionId());
		PlaceSearchRegion district = request.districtFilterId() == null
				? null
				: requiredRegion(request.districtFilterId());

		RequestStartResult start = requestExecutionService.tryStart(
				userId, UsageFeature.PLACE_SEARCH, requestId, 1);
		if (!start.started()) {
			throw new ApiException(
					ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), start.retryAfterSeconds());
		}

		RequestExecutionLease lease = start.lease();
		try {
			PlaceSearchResult result = kakaoPlaceClient.search(
					toClientRequest(request, travelRegion),
					() -> apiUsageService.acquireOrThrow(userId, UsageFeature.PLACE_SEARCH, 1));
			PlaceSearchApiResponse response = toResponse(
					result, userId, request.placeRole(), travelRegion, district);
			if (!requestExecutionService.markSucceeded(lease)) {
				throw new IllegalStateException("Request execution could not be completed");
			}
			return response;
		}
		catch (PlaceClientException exception) {
			requestExecutionService.releaseAfterFailure(lease);
			throw new ApiException(ErrorCode.PLACE_PROVIDER_UNAVAILABLE);
		}
		catch (RuntimeException exception) {
			requestExecutionService.releaseAfterFailure(lease);
			throw exception;
		}
	}

	private void validateRequest(PlaceSearchApiRequest request) {
		if (request == null || request.placeRole() != PlaceRole.ATTRACTION) {
			throw validationFailed();
		}
		if (request.center() != null && request.radiusMeters() == null) {
			throw validationFailed();
		}
		if (request.radiusMeters() == null) {
			if (request.center() != null) {
				throw validationFailed();
			}
			return;
		}
		if (!PlaceSearchRadiusPolicy.radiiMeters(request.placeRole()).contains(request.radiusMeters())) {
			throw validationFailed();
		}
	}

	private PlaceSearchRequest toClientRequest(
			PlaceSearchApiRequest request,
			PlaceSearchRegion travelRegion
	) {
		if (request.radiusMeters() == null) {
			PlaceSearchRegion.Bounds bounds = travelRegion.searchBounds();
			if (bounds == null) {
				throw validationFailed();
			}
			return PlaceSearchRequest.withinBounds(
					travelRegion.name() + " " + request.query(),
					new PlaceSearchRequest.SearchBounds(
							bounds.minLatitude(), bounds.minLongitude(),
							bounds.maxLatitude(), bounds.maxLongitude()),
					request.page(), request.size());
		}

		double latitude;
		double longitude;
		if (request.center() == null) {
			PlaceSearchRegion.Coordinate coordinate = travelRegion.representativeCoordinate();
			if (coordinate == null) {
				throw validationFailed();
			}
			latitude = coordinate.latitude();
			longitude = coordinate.longitude();
		}
		else {
			latitude = request.center().latitude();
			longitude = request.center().longitude();
		}
		return PlaceSearchRequest.around(
				request.query(),
				new PlaceSearchRequest.SearchCenter(latitude, longitude),
				request.radiusMeters(), request.page(), request.size());
	}

	private PlaceSearchApiResponse toResponse(
			PlaceSearchResult result,
			long userId,
			PlaceRole role,
			PlaceSearchRegion travelRegion,
			PlaceSearchRegion district
	) {
		Map<String, PlaceCandidate> accepted = new LinkedHashMap<>();
		for (PlaceCandidate candidate : result.places()) {
			if (matches(candidate.address(), travelRegion)
					&& (district == null || matches(candidate.address(), district))) {
				accepted.putIfAbsent(candidate.kakaoPlaceId(), candidate);
			}
		}
		List<PlaceSearchApiResponse.Place> places = accepted.values().stream()
				.map(candidate -> toResponsePlace(candidate, userId, travelRegion.regionId(), role))
				.toList();
		return new PlaceSearchApiResponse(places, result.page(), result.hasNext());
	}

	private PlaceSearchApiResponse.Place toResponsePlace(
			PlaceCandidate candidate,
			long userId,
			String regionId,
			PlaceRole role
	) {
		OptionalInt suggestion = StayDurationPolicy.suggestedMinutes(role, candidate.providerCategory());
		Integer suggestedMinutes = suggestion.isPresent() ? suggestion.getAsInt() : null;
		String token = selectionTokenService.issue(new SelectionTokenPlace(
				userId,
				regionId,
				role,
				candidate.kakaoPlaceId(),
				candidate.placeUrl(),
				candidate.latitude(),
				candidate.longitude()));
		return new PlaceSearchApiResponse.Place(
				candidate.kakaoPlaceId(), candidate.placeUrl(), candidate.providerDisplayName(),
				candidate.address(), candidate.latitude(), candidate.longitude(), suggestedMinutes, token);
	}

	private boolean matches(String address, PlaceSearchRegion region) {
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

	private PlaceSearchRegion requiredRegion(String regionId) {
		return regionService.findById(regionId).orElseThrow(this::validationFailed);
	}

	private ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED);
	}
}
