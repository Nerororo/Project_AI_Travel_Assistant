package com.example.travel.place.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@Profile({"local", "test"})
public class ProfileFakeKakaoImageClient implements KakaoImageClient {
	@Override
	public URI search(String query) {
		return null;
	}
}
