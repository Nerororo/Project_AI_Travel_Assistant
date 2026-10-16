package com.example.travel.place.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KakaoImageSearchClientTest {
	private final HttpClient http = mock(HttpClient.class);
	private final HttpResponse<String> response = mock(HttpResponse.class);
	private final KakaoImageSearchClient client = new KakaoImageSearchClient(http, new ObjectMapper(), "test-key");

	@Test
	void usesServerKeyAndReturnsDistinctSafeThumbnails() throws Exception {
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn("{\"documents\":["
				+ "{\"thumbnail_url\":\"https://search2.kakaocdn.net/argon/first\"},"
				+ "{\"thumbnail_url\":\"https://example.invalid/photo\"},"
				+ "{\"thumbnail_url\":\"https://search2.kakaocdn.net/argon/first\"},"
				+ "{\"thumbnail_url\":\"https://search2.kakaocdn.net/argon/second\"}]}");
		when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

		assertThat(client.search("테스트 장소 테스트도 테스트시"))
				.containsExactly(URI.create("https://search2.kakaocdn.net/argon/first"),
						URI.create("https://search2.kakaocdn.net/argon/second"));
		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
		assertThat(request.getValue().uri().getHost()).isEqualTo("dapi.kakao.com");
		assertThat(request.getValue().uri().getRawQuery()).contains("size=5", "query=");
		assertThat(request.getValue().headers().firstValue("Authorization")).contains("KakaoAK test-key");
	}

	@Test
	void ignoresUntrustedOrMissingThumbnail() throws Exception {
		when(response.statusCode()).thenReturn(200);
		when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		when(response.body()).thenReturn("{\"documents\":[{\"thumbnail_url\":\"https://example.invalid/photo\"}]}",
				"{\"documents\":[]}");
		assertThat(client.search("테스트 장소")).isEmpty();
		assertThat(client.search("테스트 장소")).isEmpty();
	}
}
