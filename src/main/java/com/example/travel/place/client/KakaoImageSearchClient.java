package com.example.travel.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class KakaoImageSearchClient implements KakaoImageClient {
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String restApiKey;

	public KakaoImageSearchClient(HttpClient httpClient, ObjectMapper objectMapper, String restApiKey) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.restApiKey = restApiKey;
	}

	@Override
	public URI search(String query) {
		URI uri = URI.create("https://dapi.kakao.com/v2/search/image?size=1&query="
				+ URLEncoder.encode(query, StandardCharsets.UTF_8));
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(3))
				.header("Authorization", "KakaoAK " + restApiKey)
				.header("Accept", "application/json")
				.GET().build();
		try {
			HttpResponse<String> response = httpClient.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) return null;
			ImageResponse body = objectMapper.readValue(response.body(), ImageResponse.class);
			if (body == null || body.documents() == null || body.documents().isEmpty()) return null;
			String thumbnail = body.documents().getFirst().thumbnailUrl();
			if (thumbnail == null) return null;
			URI image = URI.create(thumbnail);
			String host = image.getHost();
			return "https".equalsIgnoreCase(image.getScheme()) && host != null
					&& (host.equals("kakaocdn.net") || host.endsWith(".kakaocdn.net")) ? image : null;
		}
		catch (IOException exception) {
			return null;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ImageResponse(List<ImageDocument> documents) { }

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ImageDocument(@JsonProperty("thumbnail_url") String thumbnailUrl) { }
}
