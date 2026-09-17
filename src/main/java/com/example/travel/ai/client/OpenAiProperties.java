package com.example.travel.ai.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("routy.openai")
public record OpenAiProperties(
		@NotBlank String apiKey,
		@NotBlank String model,
		@NotNull URI endpoint,
		@NotNull Duration connectTimeout,
		@NotNull Duration requestTimeout
) {

	public OpenAiProperties {
		if (connectTimeout != null && (connectTimeout.isZero() || connectTimeout.isNegative())) {
			throw new IllegalArgumentException("connectTimeout must be positive");
		}
		if (requestTimeout != null && (requestTimeout.isZero() || requestTimeout.isNegative())) {
			throw new IllegalArgumentException("requestTimeout must be positive");
		}
	}
}
