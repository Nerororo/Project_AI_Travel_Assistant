package com.example.travel.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("routy.jwt")
public record JwtProperties(String issuer, Duration accessTokenTtl, String activeKeyId, List<SigningKey> keys) {

	public JwtProperties {
		keys = keys == null ? List.of() : List.copyOf(keys);
	}

	public record SigningKey(String id, String secret) {
	}
}
