package com.example.travel.place.client;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class KakaoLocalPlaceClient implements KakaoPlaceClient {

	private static final int MAX_ATTEMPTS = 2;
	private static final Duration MAX_ATTEMPT_TIMEOUT = Duration.ofSeconds(3);
	private static final Pattern KAKAO_PLACE_ID = Pattern.compile("[0-9]{1,30}");

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final KakaoLocalProperties properties;

	public KakaoLocalPlaceClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			KakaoLocalProperties properties
	) {
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Override
	public PlaceSearchResult search(PlaceSearchRequest request) {
		return search(request, () -> { });
	}

	@Override
	public PlaceSearchResult search(PlaceSearchRequest request, Runnable beforeRetry) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(beforeRetry, "beforeRetry must not be null");
		long deadline = System.nanoTime() + properties.requestTimeout().toNanos();
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			Duration remaining = remaining(deadline);
			if (remaining.isZero()) {
				throw failure(PlaceClientFailure.TIMEOUT);
			}
			if (attempt > 1) {
				beforeRetry.run();
			}
			remaining = remaining(deadline);
			if (remaining.isZero()) {
				throw failure(PlaceClientFailure.TIMEOUT);
			}
			try {
				HttpResponse<String> response = httpClient.send(
						buildRequest(request, min(remaining, MAX_ATTEMPT_TIMEOUT)),
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				int statusCode = response.statusCode();
				if (statusCode >= 200 && statusCode < 300) {
					return parseResponse(response.body(), request.page());
				}
				PlaceClientFailure responseFailure = mapStatus(statusCode);
				if (responseFailure != PlaceClientFailure.PROVIDER_UNAVAILABLE || attempt == MAX_ATTEMPTS) {
					throw failure(responseFailure);
				}
			}
			catch (HttpTimeoutException exception) {
				if (attempt == MAX_ATTEMPTS) {
					throw failure(PlaceClientFailure.TIMEOUT);
				}
			}
			catch (ConnectException exception) {
				if (attempt == MAX_ATTEMPTS) {
					throw failure(PlaceClientFailure.CONNECTION_FAILED);
				}
			}
			catch (IOException exception) {
				if (attempt == MAX_ATTEMPTS) {
					throw failure(PlaceClientFailure.CONNECTION_FAILED);
				}
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw failure(PlaceClientFailure.CONNECTION_FAILED);
			}
		}
		throw failure(PlaceClientFailure.PROVIDER_UNAVAILABLE);
	}

	private HttpRequest buildRequest(PlaceSearchRequest request, Duration timeout) {
		return HttpRequest.newBuilder(buildUri(request))
				.timeout(timeout)
				.header("Authorization", "KakaoAK " + properties.restApiKey())
				.header("Accept", "application/json")
				.GET()
				.build();
	}

	private URI buildUri(PlaceSearchRequest request) {
		List<String> parameters = new ArrayList<>();
		parameters.add(parameter("query", request.query()));
		parameters.add(parameter("page", Integer.toString(request.page())));
		parameters.add(parameter("size", Integer.toString(request.size())));
		if (request.center() != null) {
			parameters.add(parameter("x", Double.toString(request.center().longitude())));
			parameters.add(parameter("y", Double.toString(request.center().latitude())));
			parameters.add(parameter("radius", Integer.toString(request.radiusMeters())));
		}
		else {
			PlaceSearchRequest.SearchBounds bounds = request.bounds();
			String rect = bounds.minLongitude() + "," + bounds.minLatitude() + ","
					+ bounds.maxLongitude() + "," + bounds.maxLatitude();
			parameters.add(parameter("rect", rect));
		}
		String separator = properties.endpoint().getRawQuery() == null ? "?" : "&";
		return URI.create(properties.endpoint() + separator + String.join("&", parameters));
	}

	private String parameter(String name, String value) {
		return URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private PlaceSearchResult parseResponse(String body, int page) {
		try {
			KakaoResponse response = objectMapper.readValue(body, KakaoResponse.class);
			if (response == null || response.meta() == null || response.documents() == null) {
				throw failure(PlaceClientFailure.INVALID_RESPONSE);
			}
			List<PlaceCandidate> places = response.documents().stream()
					.map(this::mapCandidate)
					.toList();
			return new PlaceSearchResult(places, page, !response.meta().isEnd());
		}
		catch (PlaceClientException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw failure(PlaceClientFailure.INVALID_RESPONSE);
		}
	}

	private PlaceCandidate mapCandidate(KakaoDocument document) {
		if (document == null) {
			throw failure(PlaceClientFailure.INVALID_RESPONSE);
		}
		if (document.id() == null || !KAKAO_PLACE_ID.matcher(document.id()).matches()) {
			throw failure(PlaceClientFailure.INVALID_RESPONSE);
		}
		URI placeUrl = URI.create(document.placeUrl());
		if (!URI.create("https://place.map.kakao.com/" + document.id()).equals(placeUrl)) {
			throw failure(PlaceClientFailure.INVALID_RESPONSE);
		}
		double latitude = Double.parseDouble(document.y());
		double longitude = Double.parseDouble(document.x());
		if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
				|| !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
			throw failure(PlaceClientFailure.INVALID_RESPONSE);
		}
		String address = hasText(document.roadAddressName())
				? document.roadAddressName()
				: document.addressName();
		return new PlaceCandidate(
				document.id(),
				placeUrl,
				document.placeName(),
				address,
				latitude,
				longitude,
				document.categoryName());
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private PlaceClientFailure mapStatus(int statusCode) {
		return switch (statusCode) {
			case 400 -> PlaceClientFailure.INVALID_REQUEST;
			case 401 -> PlaceClientFailure.AUTHENTICATION_FAILED;
			case 403 -> PlaceClientFailure.ACCESS_DENIED;
			case 429 -> PlaceClientFailure.RATE_LIMITED;
			default -> statusCode >= 500
					? PlaceClientFailure.PROVIDER_UNAVAILABLE
					: PlaceClientFailure.INVALID_RESPONSE;
		};
	}

	private Duration remaining(long deadline) {
		long nanos = deadline - System.nanoTime();
		return nanos <= 0 ? Duration.ZERO : Duration.ofNanos(nanos);
	}

	private Duration min(Duration first, Duration second) {
		return first.compareTo(second) <= 0 ? first : second;
	}

	private PlaceClientException failure(PlaceClientFailure failure) {
		return new PlaceClientException(failure);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoResponse(KakaoMeta meta, List<KakaoDocument> documents) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoMeta(@JsonProperty("is_end") boolean isEnd) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoDocument(
			String id,
			@JsonProperty("place_url") String placeUrl,
			@JsonProperty("place_name") String placeName,
			@JsonProperty("address_name") String addressName,
			@JsonProperty("road_address_name") String roadAddressName,
			String x,
			String y,
			@JsonProperty("category_name") String categoryName
	) {
	}
}
