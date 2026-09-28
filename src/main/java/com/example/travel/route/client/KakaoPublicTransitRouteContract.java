package com.example.travel.route.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Provider request and response contract without performing external I/O.
 */
public final class KakaoPublicTransitRouteContract {

	private final ObjectMapper objectMapper;
	private final KakaoPublicTransitRouteProperties properties;

	public KakaoPublicTransitRouteContract(
			ObjectMapper objectMapper,
			KakaoPublicTransitRouteProperties properties
	) {
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	public HttpRequest request(RouteSegment segment) {
		Objects.requireNonNull(segment, "segment must not be null");
		String query = parameter("start_x", segment.origin().longitude())
				+ "&" + parameter("start_y", segment.origin().latitude())
				+ "&" + parameter("end_x", segment.destination().longitude())
				+ "&" + parameter("end_y", segment.destination().latitude());
		String separator = properties.endpoint().getRawQuery() == null ? "?" : "&";
		URI uri = URI.create(properties.endpoint() + separator + query);
		return HttpRequest.newBuilder(uri)
				.timeout(properties.requestTimeout())
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.header("Accept", "application/json")
				.GET()
				.build();
	}

	public RouteResult response(String body) {
		try {
			KakaoResponse response = objectMapper.readValue(body, KakaoResponse.class);
			if (response == null || response.status() == null) {
				throw failure(RouteClientFailure.INVALID_RESPONSE);
			}
			return switch (response.status()) {
				case "OK" -> found(response.routes());
				case "STARTNODES_NULL", "ENDNODES_NULL", "NO_RESULTS" -> RouteResult.notFound();
				case "EQUAL_POINTS" -> RouteResult.found(0);
				case "INVALID_REQUEST" -> throw failure(RouteClientFailure.INVALID_REQUEST);
				default -> throw failure(RouteClientFailure.INVALID_RESPONSE);
			};
		}
		catch (RouteClientException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw failure(RouteClientFailure.INVALID_RESPONSE);
		}
	}

	private RouteResult found(List<KakaoRoute> routes) {
		if (routes == null || routes.isEmpty()) {
			throw failure(RouteClientFailure.INVALID_RESPONSE);
		}
		KakaoRoute firstRoute = routes.getFirst();
		if (firstRoute == null || firstRoute.properties() == null
				|| firstRoute.properties().totalTime() == null
				|| firstRoute.properties().totalTime() < 0) {
			throw failure(RouteClientFailure.INVALID_RESPONSE);
		}
		return RouteResult.found(firstRoute.properties().totalTime());
	}

	private String parameter(String name, double value) {
		return URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(Double.toString(value), StandardCharsets.UTF_8);
	}

	private RouteClientException failure(RouteClientFailure failure) {
		return new RouteClientException(failure);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoResponse(String status, List<KakaoRoute> routes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoRoute(KakaoRouteProperties properties) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoRouteProperties(@JsonProperty("totalTime") Long totalTime) {
	}
}
