package com.example.travel.place.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
@Profile({"local", "test"})
public class ProfileFakeKakaoImageClient implements KakaoImageClient {
	@Override
	public List<URI> search(String query) {
		return List.of();
	}
}
