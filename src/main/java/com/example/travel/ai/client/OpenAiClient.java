package com.example.travel.ai.client;

import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.RegionCandidate;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class OpenAiClient implements AiClient {

	private static final int REGION_MAX_OUTPUT_TOKENS = 512;
	private static final int MENU_MAX_OUTPUT_TOKENS = 768;
	private static final int MAX_ATTEMPTS = 2;
	private static final long BASE_RETRY_DELAY_MILLIS = 200;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final OpenAiProperties properties;

	public OpenAiClient(HttpClient httpClient, ObjectMapper objectMapper, OpenAiProperties properties) {
		this.httpClient = httpClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public RegionRecommendationResult recommendRegions(RegionRecommendationPrompt prompt) {
		Map<String, Object> input = Map.of(
				"request", prompt.request(),
				"allowedRegions", prompt.allowedRegions()
		);
		ProviderResult providerResult = execute(
				"routy_region_recommendation",
				"Select exactly three distinct regionId values only from allowedRegions and explain each choice in Korean.",
				input,
				regionSchema(),
				REGION_MAX_OUTPUT_TOKENS
		);
		try {
			RegionPayload payload = objectMapper.readValue(providerResult.outputText(), RegionPayload.class);
			if (payload == null || payload.regions() == null) {
				throw invalidResponse();
			}
			List<RecommendedRegion> regions = payload.regions().stream()
					.map(item -> item == null ? null : new RecommendedRegion(item.regionId(), item.reason()))
					.toList();
			return new RegionRecommendationResult(regions);
		}
		catch (RuntimeException exception) {
			throw invalidResponse();
		}
	}

	@Override
	public MenuAnalysisResult analyzeMenus(MenuAnalysisPrompt prompt) {
		Map<String, Object> input = Map.of(
				"regionId", prompt.regionId(),
				"request", prompt.request(),
				"attractions", prompt.attractions()
		);
		ProviderResult providerResult = execute(
				"routy_menu_analysis",
				"Create one to five distinct Korean menu ideas and Kakao place search queries. A targetClientPlaceId must be null or come from attractions.",
				input,
				menuSchema(),
				MENU_MAX_OUTPUT_TOKENS
		);
		try {
			MenuPayload payload = objectMapper.readValue(providerResult.outputText(), MenuPayload.class);
			if (payload == null || payload.menus() == null) {
				throw invalidResponse();
			}
			List<MenuSuggestion> menus = payload.menus().stream()
					.map(item -> item == null ? null : new MenuSuggestion(
							item.name(), item.searchQuery(), item.reason(), item.targetClientPlaceId()))
					.toList();
			return new MenuAnalysisResult(menus);
		}
		catch (RuntimeException exception) {
			throw invalidResponse();
		}
	}

	private ProviderResult execute(
			String schemaName,
			String instructions,
			Object input,
			Map<String, Object> schema,
			int maxOutputTokens
	) {
		long deadline = System.nanoTime() + properties.requestTimeout().toNanos();
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			Duration remaining = remaining(deadline);
			if (remaining.isZero()) {
				throw unavailable();
			}
			try {
				HttpResponse<String> response = httpClient.send(
						buildRequest(schemaName, instructions, input, schema, maxOutputTokens, remaining),
						HttpResponse.BodyHandlers.ofString()
				);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return parseProviderResult(response.body());
				}
				if (attempt == MAX_ATTEMPTS || !isRetryable(response)) {
					throw unavailable();
				}
				sleepBeforeRetry(response, deadline);
			}
			catch (IOException exception) {
				if (attempt == MAX_ATTEMPTS) {
					throw unavailable();
				}
				sleep(retryDelayMillis(), deadline);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw unavailable();
			}
		}
		throw unavailable();
	}

	private HttpRequest buildRequest(
			String schemaName,
			String instructions,
			Object input,
			Map<String, Object> schema,
			int maxOutputTokens,
			Duration remaining
	) {
		Map<String, Object> format = new LinkedHashMap<>();
		format.put("type", "json_schema");
		format.put("name", schemaName);
		format.put("strict", true);
		format.put("schema", schema);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.model());
		body.put("reasoning", Map.of("effort", "none"));
		body.put("store", false);
		body.put("instructions", instructions);
		body.put("input", writeJson(input));
		body.put("max_output_tokens", maxOutputTokens);
		body.put("text", Map.of("format", format));

		return HttpRequest.newBuilder(properties.endpoint())
				.timeout(remaining)
				.header("Authorization", "Bearer " + properties.apiKey())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(writeJson(body)))
				.build();
	}

	private ProviderResult parseProviderResult(String body) {
		try {
			ResponseEnvelope response = objectMapper.readValue(body, ResponseEnvelope.class);
			if (!"completed".equals(response.status()) || response.error() != null
					|| response.incompleteDetails() != null || response.output() == null) {
				throw invalidResponse();
			}
			List<String> outputTexts = new ArrayList<>();
			for (OutputItem output : response.output()) {
				if (output == null || !"message".equals(output.type()) || output.content() == null) {
					continue;
				}
				for (ContentItem content : output.content()) {
					if (content == null || "refusal".equals(content.type())) {
						throw invalidResponse();
					}
					if ("output_text".equals(content.type()) && content.text() != null) {
						outputTexts.add(content.text());
					}
				}
			}
			if (outputTexts.size() != 1 || outputTexts.get(0).isBlank()) {
				throw invalidResponse();
			}
			return new ProviderResult(outputTexts.get(0));
		}
		catch (ApiException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw invalidResponse();
		}
	}

	private boolean isRetryable(HttpResponse<String> response) {
		if (response.statusCode() >= 500) {
			return true;
		}
		if (response.statusCode() != 429) {
			return false;
		}
		try {
			ProviderErrorEnvelope error = objectMapper.readValue(response.body(), ProviderErrorEnvelope.class);
			return error.error() == null || !"insufficient_quota".equals(error.error().code());
		}
		catch (RuntimeException exception) {
			return true;
		}
	}

	private void sleepBeforeRetry(HttpResponse<String> response, long deadline) {
		long delayMillis = response.headers().firstValue("Retry-After")
				.flatMap(this::parseRetryAfterSeconds)
				.map(seconds -> seconds * 1_000L)
				.orElseGet(this::retryDelayMillis);
		sleep(delayMillis, deadline);
	}

	private java.util.Optional<Long> parseRetryAfterSeconds(String value) {
		try {
			long seconds = Long.parseLong(value);
			return seconds >= 0 ? java.util.Optional.of(seconds) : java.util.Optional.empty();
		}
		catch (NumberFormatException exception) {
			return java.util.Optional.empty();
		}
	}

	private long retryDelayMillis() {
		return BASE_RETRY_DELAY_MILLIS + ThreadLocalRandom.current().nextLong(101);
	}

	private void sleep(long delayMillis, long deadline) {
		if (delayMillis <= 0) {
			return;
		}
		long remainingMillis = Math.max(0, remaining(deadline).toMillis());
		if (delayMillis >= remainingMillis) {
			throw unavailable();
		}
		try {
			Thread.sleep(delayMillis);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw unavailable();
		}
	}

	private Duration remaining(long deadline) {
		long nanos = deadline - System.nanoTime();
		return nanos <= 0 ? Duration.ZERO : Duration.ofNanos(nanos);
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException exception) {
			throw new IllegalStateException("Failed to create OpenAI request", exception);
		}
	}

	private Map<String, Object> regionSchema() {
		Map<String, Object> item = objectSchema(
				Map.of(
						"regionId", Map.of("type", "string"),
						"reason", Map.of("type", "string", "minLength", 1, "maxLength", 200)
				),
				List.of("regionId", "reason")
		);
		return objectSchema(
				Map.of("regions", Map.of("type", "array", "minItems", 3, "maxItems", 3, "items", item)),
				List.of("regions")
		);
	}

	private Map<String, Object> menuSchema() {
		Map<String, Object> item = objectSchema(
				Map.of(
						"name", Map.of("type", "string", "minLength", 1),
						"searchQuery", Map.of("type", "string", "minLength", 1),
						"reason", Map.of("type", "string", "minLength", 1),
						"targetClientPlaceId", Map.of("type", List.of("string", "null"))
				),
				List.of("name", "searchQuery", "reason", "targetClientPlaceId")
		);
		return objectSchema(
				Map.of("menus", Map.of("type", "array", "minItems", 1, "maxItems", 5, "items", item)),
				List.of("menus")
		);
	}

	private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", required);
		schema.put("additionalProperties", false);
		return schema;
	}

	private ApiException unavailable() {
		return new ApiException(ErrorCode.AI_UNAVAILABLE);
	}

	private ApiException invalidResponse() {
		return new ApiException(ErrorCode.AI_RESPONSE_INVALID);
	}

	private record ProviderResult(String outputText) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ResponseEnvelope(
			String status,
			ProviderError error,
			@JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
			List<OutputItem> output
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OutputItem(String type, List<ContentItem> content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ContentItem(String type, String text, String refusal) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ProviderErrorEnvelope(ProviderError error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ProviderError(String code) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record IncompleteDetails(String reason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RegionPayload(List<RegionItem> regions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RegionItem(String regionId, String reason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MenuPayload(List<MenuItem> menus) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MenuItem(String name, String searchQuery, String reason, String targetClientPlaceId) {
	}
}
