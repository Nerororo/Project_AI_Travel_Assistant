package com.example.travel.route.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@Profile({"prod", "smoke"})
@EnableConfigurationProperties(KakaoPublicTransitRouteProperties.class)
public class KakaoPublicTransitRouteClientConfiguration {

	@Bean
	PublicTransitRouteClient kakaoPublicTransitRouteClient(
			ObjectMapper objectMapper,
			KakaoPublicTransitRouteProperties properties
	) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		KakaoPublicTransitRouteContract contract = new KakaoPublicTransitRouteContract(
				objectMapper,
				properties);
		return new KakaoPublicTransitRouteClient(httpClient, contract);
	}
}
