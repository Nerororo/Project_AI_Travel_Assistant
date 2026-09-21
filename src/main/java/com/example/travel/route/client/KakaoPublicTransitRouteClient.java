package com.example.travel.route.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class KakaoPublicTransitRouteClient implements PublicTransitRouteClient {

	private final HttpClient httpClient;
	private final KakaoPublicTransitRouteContract contract;

	public KakaoPublicTransitRouteClient(
			HttpClient httpClient,
			KakaoPublicTransitRouteContract contract
	) {
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
		this.contract = Objects.requireNonNull(contract, "contract must not be null");
	}

	@Override
	public RouteResult findRoute(RouteSegment segment) {
		Objects.requireNonNull(segment, "segment must not be null");
		try {
			HttpResponse<String> response = httpClient.send(
					contract.request(segment),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return contract.response(response.body());
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
}
