package com.example.travel.route.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"local", "test"})
class ProfileUnavailableRouteClientsConfiguration {

	@Bean
	CarRouteClient unavailableCarRouteClient() {
		return segment -> { throw new RouteClientException(RouteClientFailure.PROVIDER_UNAVAILABLE); };
	}

	@Bean
	PublicTransitRouteClient unavailablePublicTransitRouteClient() {
		return segment -> { throw new RouteClientException(RouteClientFailure.PROVIDER_UNAVAILABLE); };
	}
}
