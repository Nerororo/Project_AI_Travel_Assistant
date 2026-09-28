package com.example.travel.route.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoPublicTransitRouteContractTest {

	private KakaoPublicTransitRouteContract contract;

	@BeforeEach
	void setUp() {
		contract = new KakaoPublicTransitRouteContract(new ObjectMapper(), properties());
	}

	@Test
	void buildsAuthenticatedWgs84AdjacentSegmentRequest() {
		HttpRequest request = contract.request(segment());

		assertThat(request.method()).isEqualTo("GET");
		assertThat(request.uri().toString()).startsWith("https://dapi.kakao.test/v2/routing/publictraffic?");
		assertThat(request.uri().getRawQuery())
				.contains(
						"start_x=127.1",
						"start_y=37.4",
						"end_x=128.2",
						"end_y=36.5")
				.doesNotContain("s_name", "e_name", "via", "date", "time", "input_coord", "output_coord");
		assertThat(request.headers().firstValue("Authorization"))
				.contains("KakaoAK unit-test-credential");
		assertThat(request.timeout()).contains(Duration.ofSeconds(6));
	}

	@Test
	void mapsOnlyFirstOkRouteTotalTimeAndDiscardsProviderDetails() {
		RouteResult result = contract.response("""
				{
				  "status":"OK",
				  "properties":{"landingURL":"https://provider.example/secret"},
				  "routes":[
				    {"properties":{"totalTime":2115,"fare":{"value":1350}},
				     "steps":[{"path":{"points":[[127.0,37.0]]}}]},
				    {"properties":{"totalTime":60}}
				  ]
				}
				""");

		assertThat(result).isEqualTo(RouteResult.found(2_115));
	}

	@ParameterizedTest
	@ValueSource(strings = {"STARTNODES_NULL", "ENDNODES_NULL", "NO_RESULTS"})
	void mapsNormalMissingRouteStatuses(String status) {
		assertThat(contract.response("{\"status\":\"" + status + "\"}"))
				.isInstanceOf(RouteResult.NotFound.class);
	}

	@Test
	void mapsEqualPointsToZero() {
		assertThat(contract.response("{\"status\":\"EQUAL_POINTS\"}"))
				.isEqualTo(RouteResult.found(0));
	}

	@Test
	void mapsInvalidRequestToNormalizedFailure() {
		assertFailure("{\"status\":\"INVALID_REQUEST\"}", RouteClientFailure.INVALID_REQUEST);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{}",
			"{\"status\":\"UNKNOWN\"}",
			"{\"status\":\"OK\"}",
			"{\"status\":\"OK\",\"routes\":[]}",
			"{\"status\":\"OK\",\"routes\":[{\"properties\":{}}]}",
			"{\"status\":\"OK\",\"routes\":[{\"properties\":{\"totalTime\":-1}},{\"properties\":{\"totalTime\":60}}]}"
	})
	void rejectsUnknownOrDamagedResponseWithoutUsingLaterCandidate(String body) {
		assertFailure(body, RouteClientFailure.INVALID_RESPONSE);
	}

	@Test
	void validatesSecurePositiveTimeoutConfiguration() {
		assertThatThrownBy(() -> new KakaoPublicTransitRouteProperties(
				"key", URI.create("http://example.com"), Duration.ofSeconds(1), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new KakaoPublicTransitRouteProperties(
				"key", URI.create("https://example.com"), Duration.ZERO, Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new KakaoPublicTransitRouteProperties(
				"key", URI.create("https://example.com"), Duration.ofSeconds(1), Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void assertFailure(String body, RouteClientFailure expected) {
		assertThatThrownBy(() -> contract.response(body))
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

	private KakaoPublicTransitRouteProperties properties() {
		return new KakaoPublicTransitRouteProperties(
				"unit-test-credential",
				URI.create("https://dapi.kakao.test/v2/routing/publictraffic"),
				Duration.ofSeconds(2),
				Duration.ofSeconds(6));
	}
}
