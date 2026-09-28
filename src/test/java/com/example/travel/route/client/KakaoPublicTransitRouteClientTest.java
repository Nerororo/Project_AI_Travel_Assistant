package com.example.travel.route.client;

import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KakaoPublicTransitRouteClientTest {

	private HttpClient httpClient;
	private HttpResponse<String> response;
	private KakaoPublicTransitRouteClient client;

	@BeforeEach
	void setUp() {
		httpClient = mock(HttpClient.class);
		response = mock(HttpResponse.class);
		client = client(httpClient);
	}

	@AfterEach
	void clearInterruptFlag() {
		Thread.interrupted();
	}

	@Test
	void sendsContractRequestOnceAndMapsFirstPublicTransitRoute() throws Exception {
		willRespond(200, """
				{"status":"OK","routes":[
				  {"properties":{"totalTime":2115},"steps":[{"path":{"points":[[127.0,37.0]]}}]},
				  {"properties":{"totalTime":60}}
				]}
				""");

		RouteResult result = client.findRoute(segment());

		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);
		assertThat(request.getValue().headers().firstValue("Authorization"))
				.contains("KakaoAK unit-test-credential");
		assertThat(request.getValue().uri().getRawQuery())
				.contains("start_x=127.1", "start_y=37.4", "end_x=128.2", "end_y=36.5")
				.doesNotContain("s_name", "e_name", "via", "date", "time");
		assertThat(result).isEqualTo(RouteResult.found(2_115));
	}

	@ParameterizedTest
	@CsvSource({
			"400, INVALID_REQUEST",
			"401, AUTHENTICATION_FAILED",
			"403, ACCESS_DENIED",
			"429, RATE_LIMITED",
			"503, PROVIDER_UNAVAILABLE",
			"404, INVALID_RESPONSE"
	})
	void mapsHttpStatusWithoutExposingProviderBody(int status, RouteClientFailure expected) throws Exception {
		willRespond(status, "provider-secret-body");

		assertFailure(expected);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);
	}

	@Test
	void mapsTimeoutAndConnectionFailureWithoutRetry() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new HttpTimeoutException("provider-secret"));

		assertFailure(RouteClientFailure.TIMEOUT);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);

		httpClient = mock(HttpClient.class);
		client = client(httpClient);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new ConnectException("provider-secret"));

		assertFailure(RouteClientFailure.CONNECTION_FAILED);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);
	}

	@Test
	void mapsOtherIoFailureWithoutRetry() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new IOException("provider-secret"));

		assertFailure(RouteClientFailure.CONNECTION_FAILED);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);
	}

	@Test
	void restoresInterruptFlagAndNormalizesInterruptedSend() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new InterruptedException("provider-secret"));

		assertFailure(RouteClientFailure.CONNECTION_FAILED);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);
	}

	@Test
	void preservesNormalMissingRouteFromContract() throws Exception {
		willRespond(200, "{\"status\":\"NO_RESULTS\"}");

		assertThat(client.findRoute(segment())).isInstanceOf(RouteResult.NotFound.class);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		verifyNoMoreInteractions(httpClient);
	}

	private KakaoPublicTransitRouteClient client(HttpClient client) {
		KakaoPublicTransitRouteProperties properties = new KakaoPublicTransitRouteProperties(
				"unit-test-credential",
				URI.create("https://dapi.kakao.test/v2/routing/publictraffic"),
				Duration.ofSeconds(2),
				Duration.ofSeconds(6));
		return new KakaoPublicTransitRouteClient(
				client,
				new KakaoPublicTransitRouteContract(new ObjectMapper(), properties));
	}

	private void willRespond(int status, String body) throws Exception {
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(response);
	}

	private void assertFailure(RouteClientFailure expected) {
		assertThatThrownBy(() -> client.findRoute(segment()))
				.isInstanceOfSatisfying(RouteClientException.class, exception -> {
					assertThat(exception.failure()).isEqualTo(expected);
					assertThat(exception.getMessage()).isEqualTo(expected.name());
					assertThat(exception.getCause()).isNull();
				});
	}

	private RouteSegment segment() {
		return new RouteSegment(
				new RouteSegment.Endpoint(37.4, 127.1),
				new RouteSegment.Endpoint(36.5, 128.2));
	}
}
