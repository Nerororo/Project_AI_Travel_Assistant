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
import com.example.travel.place.dto.HotelSearchApiRequest;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.ApiUsageService;
import com.example.travel.user.service.RequestExecutionService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotelSearchServiceTest {
	private final KakaoPlaceClient client = mock(KakaoPlaceClient.class);
	private final PlaceSearchRegionValidator validator = mock(PlaceSearchRegionValidator.class);
	private final PlaceSearchRegionService regions = mock(PlaceSearchRegionService.class);
	private final PlanningPlaceSelectionService selections = mock(PlanningPlaceSelectionService.class);
	private final SelectionTokenService tokens = mock(SelectionTokenService.class);
	private final RequestExecutionService executions = mock(RequestExecutionService.class);
	private final ApiUsageService usage = mock(ApiUsageService.class);
	private final UUID requestId = UUID.randomUUID();
	private final RequestExecutionLease lease = new RequestExecutionLease(
			7L, UsageFeature.PLACE_SEARCH, requestId, Instant.parse("2026-09-20T01:00:00Z"));
	private HotelSearchService service;

	@BeforeEach
	void setUp() {
		service = new HotelSearchService(client, validator, regions, selections, tokens, executions, usage);
		when(regions.findById("KR-CITY")).thenReturn(Optional.of(new PlaceSearchRegion(
				"KR-CITY", "테스트시", null, true, false, null, null,
				new PlaceSearchRegion.AddressBoundary(List.of("테스트도"), List.of("테스트시")))));
		when(selections.verifyAttraction("a", 7L, "KR-CITY"))
				.thenReturn(selection("1", 36.0, 127.0));
		when(selections.verifyAttraction("b", 7L, "KR-CITY"))
				.thenReturn(selection("2", 36.2, 127.2));
		when(selections.verifyAttraction("c", 7L, "KR-CITY"))
				.thenReturn(selection("3", 36.0, 127.0));
		when(executions.tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(executions.markSucceeded(lease)).thenReturn(true);
		when(tokens.issue(any(SelectionTokenPlace.class))).thenReturn("hotel-token");
	}

	@Test
	void geometricMedianDefaultsToFiveKilometersAndIssuesHotelTokenWithoutScore() {
		when(client.search(any(), any())).thenReturn(new PlaceSearchResult(List.of(
				candidate("10", "테스트도 테스트시 거리"),
				candidate("10", "테스트도 테스트시 다른거리"),
				candidate("11", "다른도 다른시 거리")), 1, false));

		var response = service.search(7L, requestId, request(HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN,
				null, null, List.of("a", "b")));

		PlaceSearchRequest providerRequest = capturedRequest();
		assertThat(providerRequest.center().latitude()).isCloseTo(36.1, org.assertj.core.data.Offset.offset(0.001));
		assertThat(providerRequest.center().longitude()).isCloseTo(127.1, org.assertj.core.data.Offset.offset(0.001));
		assertThat(providerRequest.radiusMeters()).isEqualTo(5_000);
		assertThat(response.places()).singleElement().satisfies(place -> {
			assertThat(place.kakaoPlaceId()).isEqualTo("10");
			assertThat(place.selectionToken()).isEqualTo("hotel-token");
		});
		ArgumentCaptor<SelectionTokenPlace> issued = ArgumentCaptor.forClass(SelectionTokenPlace.class);
		verify(tokens).issue(issued.capture());
		assertThat(issued.getValue().placeRole()).isEqualTo(PlaceRole.HOTEL);
		assertThat(com.example.travel.place.dto.HotelSearchApiResponse.Place.class.getRecordComponents())
				.extracting(java.lang.reflect.RecordComponent::getName)
				.doesNotContain("score", "suggestedStayMinutes", "providerCategory");
		verify(executions).markSucceeded(lease);
	}

	@Test
	void tenKilometersRequiresExplicitChoice() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(2));
		service.search(7L, requestId, request(HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN,
				10_000, null, List.of("a")));
		assertThat(capturedRequest().radiusMeters()).isEqualTo(10_000);
	}

	@Test
	void medoidUsesActualAttractionWithLowestDistanceSum() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(1));
		service.search(7L, requestId, request(HotelSearchApiRequest.Mode.MEDOID,
				null, null, List.of("a", "b", "c")));
		assertThat(capturedRequest().center())
				.isEqualTo(new PlaceSearchRequest.SearchCenter(36.0, 127.0));
		assertThat(capturedRequest().radiusMeters()).isEqualTo(5_000);
	}

	@Test
	void mapBoundsUsesRectangleInsteadOfRadius() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(1));
		var bounds = new HotelSearchApiRequest.Bounds(35.0, 126.0, 37.0, 128.0);
		service.search(7L, requestId, request(HotelSearchApiRequest.Mode.MAP_BOUNDS,
				null, bounds, List.of("a")));
		assertThat(capturedRequest().bounds()).isEqualTo(
				new PlaceSearchRequest.SearchBounds(35.0, 126.0, 37.0, 128.0));
	}

	@Test
	void malformedBoundsStopBeforeUsageAndProvider() {
		var bounds = new HotelSearchApiRequest.Bounds(37.0, 128.0, 35.0, 126.0);
		assertThatThrownBy(() -> service.search(7L, requestId,
				request(HotelSearchApiRequest.Mode.MAP_BOUNDS, null, bounds, List.of("a"))))
				.isInstanceOf(ApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
		verify(executions, never()).tryStart(anyLong(), any(), any(), anyInt());
		verify(client, never()).search(any(), any());
	}

	@Test
	void invalidModeParametersAndDuplicateAttractionsStopBeforeUsage() {
		for (HotelSearchApiRequest request : List.of(
				request(HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN, 20_000, null, List.of("a")),
				request(HotelSearchApiRequest.Mode.MEDOID, 10_000, null, List.of("a")),
				request(HotelSearchApiRequest.Mode.MAP_BOUNDS, null, null, List.of("a")),
				request(HotelSearchApiRequest.Mode.GEOMETRIC_MEDIAN, null, null, List.of("a", "a")))) {
			assertThatThrownBy(() -> service.search(7L, requestId, request))
					.isInstanceOf(ApiException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
		}
		verify(executions, never()).tryStart(anyLong(), any(), any(), anyInt());
		verify(client, never()).search(any(), any());
	}

	@Test
	void invalidAttractionTokenStopsBeforeUsageAndProvider() {
		when(selections.verifyAttraction("a", 7L, "KR-CITY"))
				.thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));
		assertThatThrownBy(() -> service.search(7L, requestId,
				request(HotelSearchApiRequest.Mode.MEDOID, null, null, List.of("a"))))
				.isInstanceOf(ApiException.class);
		verify(client, never()).search(any(), any());
	}

	@Test
	void providerFailureReleasesLeaseWithoutHotelToken() {
		when(client.search(any(), any())).thenThrow(new PlaceClientException(PlaceClientFailure.PROVIDER_UNAVAILABLE));
		assertThatThrownBy(() -> service.search(7L, requestId,
				request(HotelSearchApiRequest.Mode.MEDOID, null, null, List.of("a"))))
				.isInstanceOf(ApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_PROVIDER_UNAVAILABLE);
		verify(executions).releaseAfterFailure(lease);
		verify(tokens, never()).issue(any());
	}

	@Test
	void emptyResultsDoNotInventHotelsOrIssueTokens() {
		when(client.search(any(), any())).thenReturn(PlaceSearchResult.empty(1));
		var response = service.search(7L, requestId,
				request(HotelSearchApiRequest.Mode.MEDOID, null, null, List.of("a")));
		assertThat(response.places()).isEmpty();
		verify(tokens, never()).issue(any());
	}

	@Test
	void rateLimitStopsBeforeProvider() {
		when(executions.tryStart(7L, UsageFeature.PLACE_SEARCH, requestId, 1))
				.thenReturn(RequestStartResult.rateLimited(31));
		assertThatThrownBy(() -> service.search(7L, requestId,
				request(HotelSearchApiRequest.Mode.MEDOID, null, null, List.of("a"))))
				.isInstanceOf(ApiException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
		verify(client, never()).search(any(), any());
	}

	private PlaceSearchRequest capturedRequest() {
		ArgumentCaptor<PlaceSearchRequest> captor = ArgumentCaptor.forClass(PlaceSearchRequest.class);
		verify(client).search(captor.capture(), any());
		return captor.getValue();
	}

	private static HotelSearchApiRequest request(HotelSearchApiRequest.Mode mode, Integer radius,
			HotelSearchApiRequest.Bounds bounds, List<String> attractionTokens) {
		return new HotelSearchApiRequest("KR-CITY", attractionTokens, mode, radius, bounds, 1, 15);
	}

	private static PlanningPlaceSelection selection(String id, double latitude, double longitude) {
		return new PlanningPlaceSelection(id, URI.create("https://place.map.kakao.com/" + id),
				latitude, longitude);
	}

	private static PlaceCandidate candidate(String id, String address) {
		return new PlaceCandidate(id, URI.create("https://place.map.kakao.com/" + id),
				"provider", address, 36.0, 127.0, "category");
	}
}
