package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.client.KakaoPlaceClient;
import com.example.travel.place.client.PlaceCandidate;
import com.example.travel.place.client.PlaceClientException;
import com.example.travel.place.client.PlaceClientFailure;
import com.example.travel.place.client.PlaceSearchRequest;
import com.example.travel.place.client.PlaceSearchResult;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.RestaurantPlaceSearchRequest;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.route.algorithm.Coordinate;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class RestaurantSearchServiceTest {
	private final KakaoPlaceClient client = mock(KakaoPlaceClient.class);
	private final PlaceSearchRegionValidator validator = mock(PlaceSearchRegionValidator.class);
	private final PlaceSearchRegionService regions = mock(PlaceSearchRegionService.class);
	private final SelectionTokenService tokens = mock(SelectionTokenService.class);
	private final RequestExecutionService executions = mock(RequestExecutionService.class);
	private final ApiUsageService usage = mock(ApiUsageService.class);
	private final UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000007");
	private final RequestExecutionLease lease = new RequestExecutionLease(7L, UsageFeature.PLACE_SEARCH,
			requestId, Instant.parse("2026-09-20T01:00:00Z"));
	private RestaurantSearchService service;

	@BeforeEach
	void setUp() {
		service = new RestaurantSearchService(client, validator, regions, tokens, executions, usage);
		when(regions.findById("KR-CITY")).thenReturn(Optional.of(new PlaceSearchRegion("KR-CITY",
				"테스트시", null, true, false, null, null,
				new PlaceSearchRegion.AddressBoundary(List.of("테스트도"), List.of("테스트시")))));
		when(executions.tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(executions.markSucceeded(lease)).thenReturn(true);
		when(tokens.issue(any(SelectionTokenPlace.class))).thenReturn("restaurant-token");
	}

	@Test
	void expandsOneThreeFiveKilometersAndFiltersAddressBeforeIssuingRoleToken() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(1),
				new PlaceSearchResult(List.of(candidate("1", "다른도 다른시 거리")), 1, false),
				new PlaceSearchResult(List.of(candidate("2", "테스트도 테스트시 거리"),
						candidate("2", "테스트도 테스트시 중복")), 1, false));
		var result = service.search(7L, requestId, centered());
		ArgumentCaptor<PlaceSearchRequest> calls = ArgumentCaptor.forClass(PlaceSearchRequest.class);
		verify(client, times(3)).search(calls.capture(), any());
		assertThat(calls.getAllValues()).extracting(PlaceSearchRequest::radiusMeters)
				.containsExactly(1_000, 3_000, 5_000);
		verify(usage, times(2)).acquireOrThrow(7L, UsageFeature.PLACE_SEARCH, 1);
		assertThat(result.places()).singleElement().satisfies(place -> {
			assertThat(place.kakaoPlaceId()).isEqualTo("2");
			assertThat(place.selectionToken()).isEqualTo("restaurant-token");
		});
		ArgumentCaptor<SelectionTokenPlace> issued = ArgumentCaptor.forClass(SelectionTokenPlace.class);
		verify(tokens).issue(issued.capture());
		assertThat(issued.getValue().placeRole()).isEqualTo(PlaceRole.RESTAURANT);
		verify(executions).markSucceeded(lease);
	}

	@Test
	void mapBoundsMakesOneSearchAndProviderFailureRemainsDistinct() {
		var bounds = new RestaurantPlaceSearchRequest.Bounds(35, 126, 37, 128);
		var request = new RestaurantPlaceSearchRequest("KR-CITY", "메뉴", null, bounds, 1, 15);
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(1));
		assertThat(service.search(7L, requestId, request).places()).isEmpty();
		ArgumentCaptor<PlaceSearchRequest> captured = ArgumentCaptor.forClass(PlaceSearchRequest.class);
		verify(client).search(captured.capture(), any());
		assertThat(captured.getValue().bounds()).isEqualTo(
				new PlaceSearchRequest.SearchBounds(35, 126, 37, 128));
	}

	@Test
	void providerFailureReleasesExecutionAndReturnsServiceError() {
		when(client.search(any(), any())).thenThrow(new PlaceClientException(PlaceClientFailure.TIMEOUT));
		assertThatThrownBy(() -> service.search(7L, requestId, centered()))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLACE_PROVIDER_UNAVAILABLE));
		verify(executions).releaseAfterFailure(lease);
	}

	@Test
	void completedRequestIdStopsBeforeProviderCall() {
		when(executions.tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.thenThrow(new ApiException(ErrorCode.REQUEST_ALREADY_COMPLETED));
		assertThatThrownBy(() -> service.search(7L, requestId, centered()))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.errorCode()).isEqualTo(ErrorCode.REQUEST_ALREADY_COMPLETED));
		verify(client, never()).search(any(), any());
	}

	@Test
	void malformedBoundsStopsBeforeExecutionAndProvider() {
		var invalid = new RestaurantPlaceSearchRequest("KR-CITY", "메뉴", null,
				new RestaurantPlaceSearchRequest.Bounds(37, 128, 35, 126), 1, 15);
		assertThatThrownBy(() -> service.search(7L, requestId, invalid))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(executions, never()).tryStart(anyLong(), any(), any(), anyLong());
		verify(client, never()).search(any(), any());
	}

	private RestaurantPlaceSearchRequest centered() {
		return new RestaurantPlaceSearchRequest("KR-CITY", "메뉴", new Coordinate(36, 127),
				null, 1, 15);
	}

	private PlaceCandidate candidate(String id, String address) {
		return new PlaceCandidate(id, URI.create("https://place.map.kakao.com/" + id),
				"음식점", address, 36, 127, "FD6");
	}
}
