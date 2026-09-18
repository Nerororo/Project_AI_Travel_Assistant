package com.example.travel.place.client;

/**
 * Contract for Kakao place searches.
 *
 * <p>Implementations may perform external communication, but callers must treat every returned
 * {@link PlaceCandidate} as request-scoped data and must not persist or cache it.</p>
 */
public interface KakaoPlaceClient {

	PlaceSearchResult search(PlaceSearchRequest request);
}
