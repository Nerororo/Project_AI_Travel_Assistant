package com.example.travel.place.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("routy.kakao.local")
public record KakaoLocalProperties(
		@NotBlank String restApiKey,
		@NotNull URI endpoint,
		@NotNull Duration connectTimeout,
		@NotNull Duration requestTimeout
) {

	public KakaoLocalProperties {
		if (endpoint != null && !"https".equalsIgnoreCase(endpoint.getScheme())) {
			throw new IllegalArgumentException("endpoint must use https");
		}
		if (connectTimeout != null && (connectTimeout.isZero() || connectTimeout.isNegative())) {
			throw new IllegalArgumentException("connectTimeout must be positive");
		}
		if (requestTimeout != null && (requestTimeout.isZero() || requestTimeout.isNegative())) {
			throw new IllegalArgumentException("requestTimeout must be positive");
		}
	}
}
