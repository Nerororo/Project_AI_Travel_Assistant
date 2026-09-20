package com.example.travel.place.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class ProfileFakeKakaoPlaceClient implements KakaoPlaceClient {

	@Override
	public PlaceSearchResult search(PlaceSearchRequest request) {
		return PlaceSearchResult.empty(request.page());
	}
}
