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
import com.example.travel.place.dto.PlaceSearchApiRequest;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.RequestExecutionService;
import com.example.travel.user.service.ApiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceSearchServiceTest {

	private final KakaoPlaceClient client = mock(KakaoPlaceClient.class);
	private final PlaceSearchRegionValidator validator = mock(PlaceSearchRegionValidator.class);
	private final PlaceSearchRegionService regionService = mock(PlaceSearchRegionService.class);
	private final SelectionTokenService tokenService = mock(SelectionTokenService.class);
	private final RequestExecutionService executionService = mock(RequestExecutionService.class);
	private final ApiUsageService usageService = mock(ApiUsageService.class);
	private final UUID requestId = UUID.randomUUID();
	private final RequestExecutionLease lease = new RequestExecutionLease(
			7L, UsageFeature.PLACE_SEARCH, requestId, Instant.parse("2026-09-20T01:00:00Z"));
	private PlaceSearchService service;

	@BeforeEach
	void setUp() {
		service = new PlaceSearchService(
				client, validator, regionService, tokenService, executionService, usageService);
		when(regionService.findById("KR-CITY")).thenReturn(Optional.of(city()));
		when(executionService.tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(executionService.markSucceeded(lease)).thenReturn(true);
		when(tokenService.issue(any(SelectionTokenPlace.class))).thenReturn("signed-token");
	}

	@Test
	void searchesFromServerRegionCenterFiltersAddressAndMapsTransientFields() {
		when(client.search(any(), any())).thenReturn(new PlaceSearchResult(List.of(
				candidate("1", "테스트도 테스트시 중앙로", "문화시설 > 박물관"),
				candidate("2", "다른도 다른시 중앙로", "관광명소")), 1, false));

		var response = service.search(7L, requestId, radiusRequest(PlaceRole.ATTRACTION, 20_000));

		ArgumentCaptor<PlaceSearchRequest> providerRequest = ArgumentCaptor.forClass(PlaceSearchRequest.class);
		verify(client).search(providerRequest.capture(), any());
		assertThat(providerRequest.getValue().center())
				.isEqualTo(new PlaceSearchRequest.SearchCenter(36.0, 127.0));
		assertThat(providerRequest.getValue().radiusMeters()).isEqualTo(20_000);
		assertThat(response.places()).singleElement().satisfies(place -> {
			assertThat(place.kakaoPlaceId()).isEqualTo("1");
			assertThat(place.suggestedStayMinutes()).isEqualTo(120);
			assertThat(place.selectionToken()).isEqualTo("signed-token");
		});
		verify(executionService).tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1);
		verify(executionService).markSucceeded(lease);
	}

	@Test
	void appliesDistrictFilterAndRemovesDuplicateProviderIds() {
		when(regionService.findById("KR-DISTRICT")).thenReturn(Optional.of(district()));
		when(client.search(any(), any())).thenReturn(new PlaceSearchResult(List.of(
				candidate("1", "테스트도 테스트구 도로", "관광명소"),
				candidate("1", "테스트도 테스트구 다른로", "관광명소"),
				candidate("2", "테스트도 다른구 도로", "관광명소")), 1, false));
		PlaceSearchApiRequest request = new PlaceSearchApiRequest(
				"KR-CITY", "KR-DISTRICT", PlaceRole.ATTRACTION, "명소", null, 20_000, 1, 15);

		var response = service.search(7L, requestId, request);

		assertThat(response.places()).extracting(place -> place.kakaoPlaceId()).containsExactly("1");
	}

	@Test
	void usesBoundsAndRegionNameForWholeRegionAttractionSearch() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(3));
		PlaceSearchApiRequest request = new PlaceSearchApiRequest(
				"KR-CITY", null, PlaceRole.ATTRACTION, " 미술관 ", null, null, 3, 10);

		var response = service.search(7L, requestId, request);

		ArgumentCaptor<PlaceSearchRequest> providerRequest = ArgumentCaptor.forClass(PlaceSearchRequest.class);
		verify(client).search(providerRequest.capture(), any());
		assertThat(providerRequest.getValue().query()).isEqualTo("테스트시 미술관");
		assertThat(providerRequest.getValue().bounds()).isEqualTo(
				new PlaceSearchRequest.SearchBounds(35.0, 126.0, 37.0, 128.0));
		assertThat(response.places()).isEmpty();
		assertThat(response.page()).isEqualTo(3);
	}

	@Test
	void preservesEmptyProviderResultWithoutInventingCandidates() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(1));

		var response = service.search(7L, requestId, radiusRequest(PlaceRole.ATTRACTION, 20_000));

		assertThat(response.places()).isEmpty();
		verify(tokenService, never()).issue(any());
	}

	@Test
	void rejectsHotelAndRestaurantFromAttractionSearchBeforeUsageOrProviderCall() {
		for (PlaceRole role : List.of(PlaceRole.HOTEL, PlaceRole.RESTAURANT)) {
			assertThatThrownBy(() -> service.search(7L, requestId, radiusRequest(role, 5_000)))
					.isInstanceOfSatisfying(ApiException.class,
							exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		}

		verify(executionService, never()).tryStart(anyLong(), any(), any(), eq(1L));
		verify(client, never()).search(any(), any());
	}

	@Test
	void rejectsInvalidRoleRadiusBeforeUsageOrProviderCall() {
		assertThatThrownBy(() -> service.search(
				7L, requestId, radiusRequest(PlaceRole.RESTAURANT, 20_000)))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

		verify(executionService, never()).tryStart(anyLong(), any(), any(), eq(1L));
		verify(client, never()).search(any(), any());
	}

	@Test
	void stopsBeforeProviderWhenUsageLimitIsDenied() {
		when(executionService.tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.thenReturn(RequestStartResult.rateLimited(42));

		assertThatThrownBy(() -> service.search(
				7L, requestId, radiusRequest(PlaceRole.ATTRACTION, 20_000)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
					assertThat(exception.retryAfterSeconds()).isEqualTo(42);
				});
		verify(client, never()).search(any(), any());
	}

	@Test
	void convertsProviderFailureAndReleasesRequestIdWithoutProviderDetails() {
		when(client.search(any(), any())).thenThrow(new PlaceClientException(PlaceClientFailure.TIMEOUT));

		assertThatThrownBy(() -> service.search(
				7L, requestId, radiusRequest(PlaceRole.ATTRACTION, 20_000)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.PLACE_PROVIDER_UNAVAILABLE);
					assertThat(exception.getMessage()).doesNotContain("TIMEOUT");
				});
		verify(executionService).releaseAfterFailure(lease);
	}

	@Test
	void acquiresOneAdditionalUsagePermitBeforeProviderRetry() {
		when(client.search(any(), any())).thenAnswer(invocation -> {
			Runnable beforeRetry = invocation.getArgument(1);
			beforeRetry.run();
			return PlaceSearchResult.empty(1);
		});

		service.search(7L, requestId, radiusRequest(PlaceRole.ATTRACTION, 20_000));

		verify(usageService).acquireOrThrow(7L, UsageFeature.PLACE_SEARCH, 1);
	}

	private PlaceSearchApiRequest radiusRequest(PlaceRole role, int radius) {
		return new PlaceSearchApiRequest("KR-CITY", null, role, "검색어", null, radius, 1, 15);
	}

	private PlaceCandidate candidate(String id, String address, String category) {
		return new PlaceCandidate(
				id, URI.create("https://place.map.kakao.com/" + id), "provider", address,
				36.1, 127.1, category);
	}

	private PlaceSearchRegion city() {
		return new PlaceSearchRegion(
				"KR-CITY", "테스트시", null, true, false,
				new PlaceSearchRegion.Coordinate(36.0, 127.0),
				new PlaceSearchRegion.Bounds(35.0, 126.0, 37.0, 128.0),
				new PlaceSearchRegion.AddressBoundary(List.of("테스트도"), List.of("테스트시", "테스트구", "다른구")));
	}

	private PlaceSearchRegion district() {
		return new PlaceSearchRegion(
				"KR-DISTRICT", "테스트구", "KR-CITY", false, true,
				new PlaceSearchRegion.Coordinate(36.1, 127.1), null,
				new PlaceSearchRegion.AddressBoundary(List.of("테스트도"), List.of("테스트구")));
	}
}
