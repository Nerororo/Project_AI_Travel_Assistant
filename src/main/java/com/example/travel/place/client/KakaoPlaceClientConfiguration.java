package com.example.travel.place.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@Profile({"prod", "smoke"})
@EnableConfigurationProperties(KakaoLocalProperties.class)
public class KakaoPlaceClientConfiguration {

	@Bean
	KakaoPlaceClient kakaoPlaceClient(
			ObjectMapper objectMapper,
			KakaoLocalProperties properties
	) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		return new KakaoLocalPlaceClient(httpClient, objectMapper, properties);
	}
}
