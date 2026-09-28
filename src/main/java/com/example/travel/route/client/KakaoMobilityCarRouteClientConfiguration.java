package com.example.travel.route.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@Profile({"prod", "smoke"})
@EnableConfigurationProperties(KakaoMobilityCarRouteProperties.class)
public class KakaoMobilityCarRouteClientConfiguration {

	@Bean
	CarRouteClient kakaoMobilityCarRouteClient(
			ObjectMapper objectMapper,
			KakaoMobilityCarRouteProperties properties
	) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		return new KakaoMobilityCarRouteClient(httpClient, objectMapper, properties);
	}
}
