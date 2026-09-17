package com.example.travel.ai.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Configuration
@Profile({"prod", "smoke"})
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiClientConfiguration {

	@Bean
	HttpClient openAiHttpClient(OpenAiProperties properties) {
		return HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
	}

	@Bean
	AiClient openAiClient(HttpClient openAiHttpClient, ObjectMapper objectMapper, OpenAiProperties properties) {
		return new OpenAiClient(openAiHttpClient, objectMapper, properties);
	}
}
