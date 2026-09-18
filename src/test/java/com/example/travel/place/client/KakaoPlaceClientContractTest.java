package com.example.travel.place.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class KakaoPlaceClientContractTest {

	@Test
	void passesAValidatedSearchRequestThroughTheClientContract() {
		PlaceSearchRequest request = PlaceSearchRequest.around(
				"  museum  ",
				new PlaceSearchRequest.SearchCenter(0.0, 0.0),
				20_000,
				1,
				15
		);
		PlaceSearchResult expected = PlaceSearchResult.empty(1);
		KakaoPlaceClient client = actual -> {
			assertThat(actual).isEqualTo(request);
			return expected;
		};

		assertThat(client.search(request)).isSameAs(expected);
		assertThat(request.query()).isEqualTo("museum");
		assertThat(request.bounds()).isNull();
	}

	@Test
	void acceptsBoundsAndPreservesPageForTheClientContract() {
		PlaceSearchRequest.SearchBounds bounds = new PlaceSearchRequest.SearchBounds(
				35.0, 126.0, 36.0, 128.0);
		PlaceSearchRequest request = PlaceSearchRequest.withinBounds("museum", bounds, 45, 15);
		KakaoPlaceClient client = actual -> {
			assertThat(actual).isSameAs(request);
			return PlaceSearchResult.empty(actual.page());
		};

		PlaceSearchResult result = client.search(request);

		assertThat(request.center()).isNull();
		assertThat(request.radiusMeters()).isNull();
		assertThat(request.bounds()).isSameAs(bounds);
		assertThat(result.page()).isEqualTo(45);
	}

	@Test
	void requiresExactlyOneCompleteSpatialInput() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest("museum", null, null, null, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest(
						"museum", null, 1_000, null, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), null, null, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), 1_000,
						new PlaceSearchRequest.SearchBounds(35.0, 126.0, 36.0, 128.0), 1, 15));
	}

	@Test
	void validatesOfficialRadiusAndPagingBounds() {
		assertThat(PlaceSearchRequest.around(
				"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), 0, 1, 1).radiusMeters())
				.isZero();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> PlaceSearchRequest.around(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), -1, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> PlaceSearchRequest.around(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), 20_001, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> PlaceSearchRequest.around(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), 1_000, 0, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> PlaceSearchRequest.around(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), 1_000, 46, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> PlaceSearchRequest.around(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), 1_000, 1, 16));
	}

	@Test
	void validatesRequestScopedCoordinates() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchCenter(Double.NaN, 0.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchCenter(0.0, 180.000001));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchBounds(
						Double.NaN, 126.0, 36.0, 128.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchBounds(
						36.0, 126.0, 35.0, 128.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchBounds(
						35.0, 128.0, 36.0, 126.0));
	}

	@Test
	void representsAnEmptyProviderResultAsSuccess() {
		PlaceSearchResult result = PlaceSearchResult.empty(3);

		assertThat(result.places()).isEmpty();
		assertThat(result.page()).isEqualTo(3);
		assertThat(result.hasNext()).isFalse();
	}

	@Test
	void defensivelyCopiesTransientCandidates() {
		List<PlaceCandidate> mutableCandidates = new ArrayList<>();
		mutableCandidates.add(candidate());

		PlaceSearchResult result = new PlaceSearchResult(mutableCandidates, 1, true);
		mutableCandidates.clear();

		assertThat(result.places()).containsExactly(candidate());
		assertThat(result.places()).isUnmodifiable();
	}

	@Test
	void rejectsMissingOrInvalidProviderFields() {
		assertThatNullPointerException()
				.isThrownBy(() -> new PlaceCandidate(
						null, URI.create("https://place.map.kakao.com/1"), "provider name", "provider address",
						0.0, 0.0, "category"));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceCandidate(
						"1", URI.create("http://place.map.kakao.com/1"), "provider name", "provider address",
						0.0, 0.0, "category"));
	}

	@Test
	void exposesOnlyNormalizedFailureInformation() {
		PlaceClientException exception = new PlaceClientException(PlaceClientFailure.TIMEOUT);

		assertThat(exception.failure()).isEqualTo(PlaceClientFailure.TIMEOUT);
		assertThat(exception.getMessage()).isEqualTo("TIMEOUT");
		assertThat(exception.getCause()).isNull();
		assertThat(exception.getSuppressed()).isEmpty();
		assertThat(PlaceClientFailure.TIMEOUT.isTechnicalFailure()).isTrue();
		assertThat(PlaceClientFailure.INVALID_REQUEST.isTechnicalFailure()).isFalse();
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
