package com.example.travel.place.service;

import com.example.travel.place.client.KakaoPlaceClient;
import com.example.travel.place.client.PlaceSearchRequest;
import com.example.travel.place.client.PlaceSearchResult;

import java.util.Objects;

/**
 * Thin place-client boundary. Search policies and API mapping are added in later place tasks.
 */
public class PlaceService {

	private final KakaoPlaceClient kakaoPlaceClient;

	public PlaceService(KakaoPlaceClient kakaoPlaceClient) {
		this.kakaoPlaceClient = Objects.requireNonNull(kakaoPlaceClient, "kakaoPlaceClient must not be null");
	}

	public PlaceSearchResult search(PlaceSearchRequest request) {
		return kakaoPlaceClient.search(Objects.requireNonNull(request, "request must not be null"));
	}
}
