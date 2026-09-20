package com.example.travel.place.config;

import com.example.travel.place.service.PlaceSearchRegionValidator;
import com.example.travel.place.service.SelectionTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.util.Base64;

public final class SelectionTokenConfiguration {

	private SelectionTokenConfiguration() {
	}

	@Configuration
	@Profile({"prod", "smoke"})
	@EnableConfigurationProperties(SelectionTokenProperties.class)
	static class ExternalKeyConfiguration {

		@Bean
		SelectionTokenService selectionTokenService(
				SelectionTokenProperties properties,
				Clock clock,
				PlaceSearchRegionValidator regionValidator
		) {
			return new SelectionTokenService(properties, clock, regionValidator);
		}
	}

	@Configuration
	@Profile({"local", "test"})
	static class EphemeralKeyConfiguration {

		@Bean
		SelectionTokenService selectionTokenService(
				Clock clock,
				PlaceSearchRegionValidator regionValidator
		) {
			byte[] secret = new byte[32];
			new java.security.SecureRandom().nextBytes(secret);
			SelectionTokenProperties properties = new SelectionTokenProperties(
					"routy", "ephemeral", java.util.List.of(
							new SelectionTokenProperties.SigningKey(
									"ephemeral", Base64.getEncoder().encodeToString(secret))));
			return new SelectionTokenService(properties, clock, regionValidator);
		}
	}
}
