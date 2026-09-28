package com.example.travel.route.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class KakaoMobilityCarRouteClientTest {

	private HttpClient httpClient;
	private HttpResponse<String> response;
	private KakaoMobilityCarRouteClient client;

	@BeforeEach
	void setUp() {
		httpClient = mock(HttpClient.class);
		response = mock(HttpResponse.class);
		client = new KakaoMobilityCarRouteClient(httpClient, new ObjectMapper(), properties());
	}

	@Test
	void sendsOnlyAdjacentEndpointsAndSummaryOptionsThenMapsDuration() throws Exception {
		willRespond(200, """
				{"routes":[{"result_code":0,"result_msg":"success","summary":{"duration":422},
				"sections":[{"roads":[{"vertexes":[127.0,37.0]}]}]}]}
				""");

		RouteResult result = client.findRoute(segment());

		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
		assertThat(request.getValue().headers().firstValue("Authorization"))
				.contains("KakaoAK unit-test-credential");
		assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(6));
		assertThat(request.getValue().uri().getRawQuery())
				.contains(
						"origin=127.1%2C37.4",
						"destination=128.2%2C36.5",
						"priority=RECOMMEND",
						"alternatives=false",
						"summary=true")
				.doesNotContain("waypoints", "name=", "angle=", "road_details");
		assertThat(result).isEqualTo(RouteResult.found(422));
	}

	@ParameterizedTest
	@CsvSource({"1", "102", "103", "105", "106"})
	void mapsNormalMissingRouteResultCodes(int resultCode) throws Exception {
		willRespond(200, "{\"routes\":[{\"result_code\":" + resultCode + ",\"result_msg\":\"provider detail\"}]}");

		assertThat(client.findRoute(segment())).isInstanceOf(RouteResult.NotFound.class);
	}

	@Test
	void mapsNearbyPointsToZeroWithoutRequiringSummary() throws Exception {
		willRespond(200, "{\"routes\":[{\"result_code\":104,\"result_msg\":\"provider detail\"}]}");

		assertThat(client.findRoute(segment())).isEqualTo(RouteResult.found(0));
	}

	@ParameterizedTest
	@CsvSource({"101", "107", "999"})
	void rejectsWaypointOnlyAndUnknownResultCodes(int resultCode) throws Exception {
		willRespond(200, "{\"routes\":[{\"result_code\":" + resultCode + ",\"result_msg\":\"provider-secret\"}]}");

		assertFailure(RouteClientFailure.INVALID_RESPONSE);
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
	}

	@Test
	void mapsTimeoutAndConnectionFailureWithoutRetry() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new HttpTimeoutException("provider-secret"));
		assertFailure(RouteClientFailure.TIMEOUT);
		verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

		httpClient = mock(HttpClient.class);
		client = new KakaoMobilityCarRouteClient(httpClient, new ObjectMapper(), properties());
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenThrow(new ConnectException("provider-secret"));
		assertFailure(RouteClientFailure.CONNECTION_FAILED);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{}",
			"{\"routes\":[]}",
			"{\"routes\":[{\"result_code\":0}]}",
			"{\"routes\":[{\"result_code\":0,\"summary\":{\"duration\":-1}}]}"
	})
	void rejectsIncompleteOrInvalidSuccessResponse(String body) throws Exception {
		willRespond(200, body);

		assertFailure(RouteClientFailure.INVALID_RESPONSE);
	}

	@Test
	void validatesSecurePositiveTimeoutConfiguration() {
		assertThatThrownBy(() -> new KakaoMobilityCarRouteProperties(
				"key", URI.create("http://example.com"), Duration.ofSeconds(1), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new KakaoMobilityCarRouteProperties(
				"key", URI.create("https://example.com"), Duration.ZERO, Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new KakaoMobilityCarRouteProperties(
				"key", URI.create("https://example.com"), Duration.ofSeconds(1), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void willRespond(int status, String body) throws IOException, InterruptedException {
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

	private KakaoMobilityCarRouteProperties properties() {
		return new KakaoMobilityCarRouteProperties(
				"unit-test-credential",
				URI.create("https://apis-navi.kakao.test/v1/directions"),
				Duration.ofSeconds(2),
				Duration.ofSeconds(6));
	}
}
