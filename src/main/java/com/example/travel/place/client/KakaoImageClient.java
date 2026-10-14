package com.example.travel.place.client;

import java.net.URI;
import java.util.List;

public interface KakaoImageClient {
	List<URI> search(String query);
}
