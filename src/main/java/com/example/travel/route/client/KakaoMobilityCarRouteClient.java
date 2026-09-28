package com.example.travel.route.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class KakaoMobilityCarRouteClient implements CarRouteClient {

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final KakaoMobilityCarRouteProperties properties;

	public KakaoMobilityCarRouteClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			KakaoMobilityCarRouteProperties properties
	) {
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Override
	public RouteResult findRoute(RouteSegment segment) {
		Objects.requireNonNull(segment, "segment must not be null");
		try {
			HttpResponse<String> response = httpClient.send(
					buildRequest(segment),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return parseResponse(response.body());
			}
			throw failure(mapStatus(response.statusCode()));
		}
		catch (HttpTimeoutException exception) {
			throw failure(RouteClientFailure.TIMEOUT);
		}
		catch (ConnectException exception) {
			throw failure(RouteClientFailure.CONNECTION_FAILED);
		}
		catch (IOException exception) {
			throw failure(RouteClientFailure.CONNECTION_FAILED);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw failure(RouteClientFailure.CONNECTION_FAILED);
		}
	}

	private HttpRequest buildRequest(RouteSegment segment) {
		String origin = coordinate(segment.origin());
		String destination = coordinate(segment.destination());
		String query = parameter("origin", origin)
				+ "&" + parameter("destination", destination)
				+ "&" + parameter("priority", "RECOMMEND")
				+ "&" + parameter("alternatives", "false")
				+ "&" + parameter("summary", "true");
		String separator = properties.endpoint().getRawQuery() == null ? "?" : "&";
		URI uri = URI.create(properties.endpoint() + separator + query);
		return HttpRequest.newBuilder(uri)
				.timeout(properties.requestTimeout())
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.header("Accept", "application/json")
				.GET()
				.build();
	}

	private String coordinate(RouteSegment.Endpoint endpoint) {
		return endpoint.longitude() + "," + endpoint.latitude();
	}

	private String parameter(String name, String value) {
		return URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private RouteResult parseResponse(String body) {
		try {
			KakaoResponse response = objectMapper.readValue(body, KakaoResponse.class);
			if (response == null || response.routes() == null || response.routes().isEmpty()) {
				throw failure(RouteClientFailure.INVALID_RESPONSE);
			}
			KakaoRoute route = response.routes().getFirst();
			if (route == null || route.resultCode() == null) {
				throw failure(RouteClientFailure.INVALID_RESPONSE);
			}
			return mapResult(route);
		}
		catch (RouteClientException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw failure(RouteClientFailure.INVALID_RESPONSE);
		}
	}

	private RouteResult mapResult(KakaoRoute route) {
		return switch (route.resultCode()) {
			case 0 -> found(route.summary());
			case 1, 102, 103, 105, 106 -> RouteResult.notFound();
			case 104 -> RouteResult.found(0);
			case 101, 107 -> throw failure(RouteClientFailure.INVALID_RESPONSE);
			default -> throw failure(RouteClientFailure.INVALID_RESPONSE);
		};
	}

	private RouteResult found(KakaoSummary summary) {
		if (summary == null || summary.duration() == null || summary.duration() < 0) {
			throw failure(RouteClientFailure.INVALID_RESPONSE);
		}
		return RouteResult.found(summary.duration());
	}

	private RouteClientFailure mapStatus(int statusCode) {
		return switch (statusCode) {
			case 400 -> RouteClientFailure.INVALID_REQUEST;
			case 401 -> RouteClientFailure.AUTHENTICATION_FAILED;
			case 403 -> RouteClientFailure.ACCESS_DENIED;
			case 429 -> RouteClientFailure.RATE_LIMITED;
			default -> statusCode >= 500
					? RouteClientFailure.PROVIDER_UNAVAILABLE
					: RouteClientFailure.INVALID_RESPONSE;
		};
	}

	private RouteClientException failure(RouteClientFailure failure) {
		return new RouteClientException(failure);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoResponse(List<KakaoRoute> routes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoRoute(
			@JsonProperty("result_code") Integer resultCode,
			KakaoSummary summary
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoSummary(Long duration) {
	}
}
