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
		PlaceSearchRequest request = new PlaceSearchRequest(
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
	}

	@Test
	void requiresCenterAndRadiusTogetherAndValidPaging() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest("museum", null, 1_000, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest(
						"museum", new PlaceSearchRequest.SearchCenter(0.0, 0.0), null, 1, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest("museum", null, null, 0, 15));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest("museum", null, null, 1, 16));
	}

	@Test
	void validatesRequestScopedCoordinates() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchCenter(Double.NaN, 0.0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlaceSearchRequest.SearchCenter(0.0, 180.000001));
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
