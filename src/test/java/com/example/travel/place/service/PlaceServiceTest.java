package com.example.travel.place.service;

import com.example.travel.place.client.FakeKakaoPlaceClient;
import com.example.travel.place.client.PlaceCandidate;
import com.example.travel.place.client.PlaceClientException;
import com.example.travel.place.client.PlaceClientFailure;
import com.example.travel.place.client.PlaceSearchRequest;
import com.example.travel.place.client.PlaceSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceServiceTest {

	private FakeKakaoPlaceClient client;
	private PlaceService service;

	@BeforeEach
	void setUp() {
		client = new FakeKakaoPlaceClient();
		service = new PlaceService(client);
	}

	@Test
	void delegatesTheRequestAndReturnsSuccessfulCandidates() {
		PlaceSearchRequest request = request();
		PlaceSearchResult expected = new PlaceSearchResult(List.of(candidate()), 1, true);
		client.willReturn(expected);

		PlaceSearchResult actual = service.search(request);

		assertThat(actual).isSameAs(expected);
		assertThat(client.lastRequest()).isSameAs(request);
		assertThat(client.callCount()).isEqualTo(1);
	}

	@Test
	void preservesAnEmptySearchAsASuccessfulResult() {
		client.willReturn(PlaceSearchResult.empty(1));

		PlaceSearchResult result = service.search(request());

		assertThat(result.places()).isEmpty();
		assertThat(result.hasNext()).isFalse();
		assertThat(client.callCount()).isEqualTo(1);
	}

	@Test
	void propagatesTimeoutWithoutRetrying() {
		client.willFailWith(PlaceClientFailure.TIMEOUT);

		assertFailure(PlaceClientFailure.TIMEOUT);
		assertThat(client.callCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@EnumSource(value = PlaceClientFailure.class, names = {
			"INVALID_REQUEST", "AUTHENTICATION_FAILED", "ACCESS_DENIED", "RATE_LIMITED"
	})
	void propagatesFourXxFailuresWithoutRetrying(PlaceClientFailure failure) {
		client.willFailWith(failure);

		assertFailure(failure);
		assertThat(client.callCount()).isEqualTo(1);
	}

	@Test
	void propagatesFiveXxFailureWithoutRetrying() {
		client.willFailWith(PlaceClientFailure.PROVIDER_UNAVAILABLE);

		assertFailure(PlaceClientFailure.PROVIDER_UNAVAILABLE);
		assertThat(client.callCount()).isEqualTo(1);
	}

	@Test
	void rejectsMissingDependenciesAndRequestsBeforeCallingTheClient() {
		assertThatNullPointerException()
				.isThrownBy(() -> new PlaceService(null));
		assertThatNullPointerException()
				.isThrownBy(() -> service.search(null));
		assertThat(client.callCount()).isZero();
	}

	private void assertFailure(PlaceClientFailure expected) {
		assertThatThrownBy(() -> service.search(request()))
				.isInstanceOfSatisfying(PlaceClientException.class,
						exception -> assertThat(exception.failure()).isEqualTo(expected));
	}

	private PlaceSearchRequest request() {
		return new PlaceSearchRequest(
				"museum",
				new PlaceSearchRequest.SearchCenter(0.0, 0.0),
				20_000,
				1,
				15
		);
	}

	private PlaceCandidate candidate() {
		return new PlaceCandidate(
				"1",
				URI.create("https://place.map.kakao.com/1"),
				"provider name",
				"provider address",
				0.0,
				0.0,
				"provider category"
		);
	}
}
