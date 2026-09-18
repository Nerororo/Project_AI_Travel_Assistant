package com.example.travel.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("routy.place.selection-token")
public record SelectionTokenProperties(String issuer, String activeKeyId, List<SigningKey> keys) {

	public SelectionTokenProperties {
		keys = keys == null ? List.of() : List.copyOf(keys);
	}

	public record SigningKey(String id, String secret) {
	}
}
