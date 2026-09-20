package com.example.travel.place.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KakaoLocalPlaceClientTest {

	private HttpClient httpClient;
	private HttpResponse<String> response;
	private KakaoLocalPlaceClient client;

	@BeforeEach
	void setUp() {
		httpClient = mock(HttpClient.class);
		response = mock(HttpResponse.class);
		client = new KakaoLocalPlaceClient(httpClient, new ObjectMapper(), properties(Duration.ofSeconds(6)));
	}

	@Test
	void sendsAuthenticatedRadiusRequestAndMapsProviderResponse() throws Exception {
		willRespond(200, """
				{"meta":{"is_end":false},"documents":[{
				  "id":"123","place_url":"https://place.map.kakao.com/123",
				  "place_name":"provider display","address_name":"parcel address",
				  "road_address_name":"road address","x":"129.1604","y":"35.1587",
				  "category_name":"culture > museum"
				}]}
				""");

		PlaceSearchResult result = client.search(PlaceSearchRequest.around(
				"해변 museum", new PlaceSearchRequest.SearchCenter(35.1587, 129.1604), 20_000, 2, 15));

		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
		assertThat(request.getValue().headers().firstValue("Authorization"))
				.contains("KakaoAK unit-test-credential");
		assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(3));
		assertThat(request.getValue().uri().getRawQuery())
				.contains("query=%ED%95%B4%EB%B3%80+museum", "page=2", "size=15",
						"x=129.1604", "y=35.1587", "radius=20000")
				.doesNotContain("rect=");
		assertThat(result.page()).isEqualTo(2);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.places()).singleElement().satisfies(place -> {
			assertThat(place.kakaoPlaceId()).isEqualTo("123");
			assertThat(place.address()).isEqualTo("road address");
			assertThat(place.latitude()).isEqualTo(35.1587);
			assertThat(place.longitude()).isEqualTo(129.1604);
		});
	}

	@Test
	void serializesBoundsInKakaoRectOrderAndMapsEmptyResult() throws Exception {
		willRespond(200, "{\"meta\":{\"is_end\":true},\"documents\":[]}");

		PlaceSearchResult result = client.search(PlaceSearchRequest.withinBounds(
				"museum", new PlaceSearchRequest.SearchBounds(35.0, 126.0, 36.0, 128.0), 1, 10));

		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
		assertThat(request.getValue().uri().getRawQuery())
				.contains("rect=126.0%2C35.0%2C128.0%2C36.0")
				.doesNotContain("&x=", "&y=", "&radius=");
		assertThat(result.places()).isEmpty();
		assertThat(result.hasNext()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({
			"400, INVALID_REQUEST",
			"401, AUTHENTICATION_FAILED",
			"403, ACCESS_DENIED",
			"429, RATE_LIMITED",
			"404, INVALID_RESPONSE"
	})
	void convertsNonRetryableStatusesWithoutExposingProviderBody(
			int status,
			PlaceClientFailure expected
	) throws Exception {
		willRespond(status, "provider-secret-body");

		assertFailure(expected);

		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void retriesOneServerFailureAndThenSucceeds() throws Exception {
		HttpResponse<String> unavailable = mock(HttpResponse.class);
		HttpResponse<String> success = mock(HttpResponse.class);
		when(unavailable.statusCode()).thenReturn(503);
		when(success.statusCode()).thenReturn(200);
		when(success.body()).thenReturn("{\"meta\":{\"is_end\":true},\"documents\":[]}");
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(unavailable, success);

		Runnable beforeRetry = mock(Runnable.class);

		assertThat(client.search(request(), beforeRetry).places()).isEmpty();
		verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verify(beforeRetry).run();
	}

	@Test
	void convertsTimeoutAndConnectionFailureAfterOneRetry() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new HttpTimeoutException("provider detail"));
		assertFailure(PlaceClientFailure.TIMEOUT);
		verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

		httpClient = mock(HttpClient.class);
		client = new KakaoLocalPlaceClient(httpClient, new ObjectMapper(), properties(Duration.ofSeconds(6)));
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new ConnectException("provider detail"));
		assertFailure(PlaceClientFailure.CONNECTION_FAILED);
	}

	@Test
	void rejectsMalformedOrIncompleteSuccessWithoutRetrying() throws Exception {
		willRespond(200, "{\"documents\":[]}");

		assertFailure(PlaceClientFailure.INVALID_RESPONSE);

		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"abc|https://place.map.kakao.com/abc|127.0|36.0",
			"123|https://place.map.kakao.com/456|127.0|36.0",
			"123|https://example.com/123|127.0|36.0",
			"123|https://place.map.kakao.com/123|127.0|40.0"
	})
	void rejectsInvalidProviderIdentityUrlOrKoreaCoordinate(
			String id, String placeUrl, String x, String y
	) throws Exception {
		willRespond(200, """
				{"meta":{"is_end":true},"documents":[{
				  "id":"%s","place_url":"%s","place_name":"provider",
				  "address_name":"address","road_address_name":"","x":"%s","y":"%s",
				  "category_name":"category"
				}]}
				""".formatted(id, placeUrl, x, y));

		assertFailure(PlaceClientFailure.INVALID_RESPONSE);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void doesNotReserveRetryUsageWhenOverallDeadlineAlreadyExpired() throws Exception {
		client = new KakaoLocalPlaceClient(httpClient, new ObjectMapper(), properties(Duration.ofMillis(10)));
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenAnswer(invocation -> {
					Thread.sleep(30);
					throw new HttpTimeoutException("provider detail");
				});
		Runnable beforeRetry = mock(Runnable.class);

		assertThatThrownBy(() -> client.search(request(), beforeRetry))
				.isInstanceOfSatisfying(PlaceClientException.class,
						exception -> assertThat(exception.failure()).isEqualTo(PlaceClientFailure.TIMEOUT));

		verify(beforeRetry, never()).run();
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void validatesSecureConfiguration() {
		assertThatThrownBy(() -> new KakaoLocalProperties(
				"key", URI.create("http://example.com"), Duration.ofSeconds(1), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> properties(Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void willRespond(int status, String body) throws IOException, InterruptedException {
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(response);
	}

	private void assertFailure(PlaceClientFailure expected) {
		assertThatThrownBy(() -> client.search(request()))
				.isInstanceOfSatisfying(PlaceClientException.class, exception -> {
					assertThat(exception.failure()).isEqualTo(expected);
					assertThat(exception.getMessage()).isEqualTo(expected.name());
					assertThat(exception.getCause()).isNull();
				});
	}

	private PlaceSearchRequest request() {
		return PlaceSearchRequest.around(
				"museum", new PlaceSearchRequest.SearchCenter(35.0, 127.0), 20_000, 1, 15);
	}

	private KakaoLocalProperties properties(Duration requestTimeout) {
		return new KakaoLocalProperties(
				"unit-test-credential",
				URI.create("https://dapi.kakao.test/v2/local/search/keyword.json"),
				Duration.ofSeconds(2),
				requestTimeout);
	}
}
