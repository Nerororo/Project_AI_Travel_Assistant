package com.example.travel.place.client;

import java.util.Objects;

public final class FakeKakaoPlaceClient implements KakaoPlaceClient {

	private PlaceSearchResult result;
	private PlaceClientFailure failure;
	private PlaceSearchRequest lastRequest;
	private int callCount;

	public void willReturn(PlaceSearchResult result) {
		this.result = Objects.requireNonNull(result, "result must not be null");
		this.failure = null;
	}

	public void willFailWith(PlaceClientFailure failure) {
		this.failure = Objects.requireNonNull(failure, "failure must not be null");
		this.result = null;
	}

	@Override
	public PlaceSearchResult search(PlaceSearchRequest request) {
		lastRequest = Objects.requireNonNull(request, "request must not be null");
		callCount++;
		if (failure != null) {
			throw new PlaceClientException(failure);
		}
		if (result == null) {
			throw new IllegalStateException("FakeKakaoPlaceClient behavior is not configured");
		}
		return result;
	}

	public PlaceSearchRequest lastRequest() {
		return lastRequest;
	}

	public int callCount() {
		return callCount;
	}
}
