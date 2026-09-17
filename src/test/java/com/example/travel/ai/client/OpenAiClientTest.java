package com.example.travel.ai.client;

import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.RegionCandidate;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Queue<StubResponse> responses = new ArrayDeque<>();
	private final List<CapturedRequest> requests = new ArrayList<>();
	private HttpServer server;
	private OpenAiClient client;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/v1/responses", this::handle);
		server.start();

		var properties = new OpenAiProperties(
				"unit-test-credential",
				"gpt-5.6-luna",
				URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/responses"),
				Duration.ofSeconds(3),
				Duration.ofSeconds(15)
		);
		client = new OpenAiClient(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build(),
				objectMapper,
				properties
		);
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void sendsStrictNonStoredRegionRequestAndMapsOutput() {
		responses.add(okResponse(Map.of("regions", List.of(
				Map.of("regionId", "region-a", "reason", "이유 A"),
				Map.of("regionId", "region-b", "reason", "이유 B"),
				Map.of("regionId", "region-c", "reason", "이유 C")
		))));

		var result = client.recommendRegions(new AiClient.RegionRecommendationPrompt(
				"조용한 여행",
				List.of(
						new RegionCandidate("region-a", "지역 A", null),
						new RegionCandidate("region-b", "지역 B", null),
						new RegionCandidate("region-c", "지역 C", null)
				)
		));

		assertThat(result.regions()).extracting(AiClient.RecommendedRegion::regionId)
				.containsExactly("region-a", "region-b", "region-c");
		assertThat(requests).hasSize(1);
		assertThat(requests.get(0).authorization()).isEqualTo("Bearer unit-test-credential");
		Map<String, Object> body = readMap(requests.get(0).body());
		assertThat(body.get("model")).isEqualTo("gpt-5.6-luna");
		assertThat(body.get("store")).isEqualTo(false);
		assertThat(body.get("max_output_tokens")).isEqualTo(512);
		assertThat(((Map<?, ?>) body.get("reasoning")).get("effort")).isEqualTo("none");
		Map<?, ?> format = (Map<?, ?>) ((Map<?, ?>) body.get("text")).get("format");
		assertThat(format.get("type")).isEqualTo("json_schema");
		assertThat(format.get("strict")).isEqualTo(true);
		assertThat(((Map<?, ?>) format.get("schema")).get("additionalProperties")).isEqualTo(false);
		assertThat(body).doesNotContainKeys("tools", "metadata", "conversation", "previous_response_id");
	}

	@Test
	void mapsMenuOutputWithNullableTarget() {
		responses.add(okResponse(Map.of("menus", List.of(
				Map.of(
						"name", "메뉴 A",
						"searchQuery", "검색어 A",
						"reason", "이유 A",
						"targetClientPlaceId", "place-a"
				)
		))));

		var result = client.analyzeMenus(new AiClient.MenuAnalysisPrompt(
				"region-a",
				"음식 요청",
				List.of(new AttractionContext("place-a", "사용자 장소 A"))
		));

		assertThat(result.menus()).hasSize(1);
		assertThat(result.menus().get(0).targetClientPlaceId()).isEqualTo("place-a");
		assertThat(readMap(requests.get(0).body()).get("max_output_tokens")).isEqualTo(768);
	}

	@Test
	void retriesOneTemporaryServerFailure() {
		responses.add(new StubResponse(503, "{}", Map.of()));
		responses.add(okResponse(Map.of("regions", List.of(
				Map.of("regionId", "region-a", "reason", "이유 A"),
				Map.of("regionId", "region-b", "reason", "이유 B"),
				Map.of("regionId", "region-c", "reason", "이유 C")
		))));

		client.recommendRegions(regionPrompt());

		assertThat(requests).hasSize(2);
	}

	@Test
	void doesNotRetryBadRequestOrExhaustedQuota() {
		responses.add(new StubResponse(400, "{}", Map.of()));
		assertUnavailable(() -> client.recommendRegions(regionPrompt()));
		assertThat(requests).hasSize(1);

		requests.clear();
		responses.add(new StubResponse(429, "{\"error\":{\"code\":\"insufficient_quota\"}}", Map.of()));
		assertUnavailable(() -> client.recommendRegions(regionPrompt()));
		assertThat(requests).hasSize(1);
	}

	@Test
	void convertsRequestTimeoutWithoutUnboundedRetries() {
		responses.add(new StubResponse(200, "{}", Map.of(), 200));
		var timeoutProperties = new OpenAiProperties(
				"unit-test-credential",
				"gpt-5.6-luna",
				URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/responses"),
				Duration.ofSeconds(3),
				Duration.ofMillis(50)
		);
		client = new OpenAiClient(
				HttpClient.newBuilder().connectTimeout(timeoutProperties.connectTimeout()).build(),
				objectMapper,
				timeoutProperties
		);

		assertUnavailable(() -> client.recommendRegions(regionPrompt()));
		assertThat(requests).hasSize(1);
	}

	@Test
	void rejectsRefusalIncompleteAndMalformedStructuredOutputWithoutExposingProviderBody() {
		responses.add(new StubResponse(200, json(Map.of(
				"status", "completed",
				"output", List.of(Map.of(
						"type", "message",
						"content", List.of(Map.of("type", "refusal", "refusal", "provider text"))
				))
		)), Map.of()));
		assertInvalid(() -> client.recommendRegions(regionPrompt()));

		responses.add(new StubResponse(200, json(Map.of(
				"status", "incomplete",
				"incomplete_details", Map.of("reason", "max_output_tokens"),
				"output", List.of()
		)), Map.of()));
		assertInvalid(() -> client.recommendRegions(regionPrompt()));

		responses.add(okResponse(Map.of("unexpected", true)));
		assertInvalid(() -> client.recommendRegions(regionPrompt()));
	}

	private AiClient.RegionRecommendationPrompt regionPrompt() {
		return new AiClient.RegionRecommendationPrompt("여행 요청", List.of(
				new RegionCandidate("region-a", "지역 A", null),
				new RegionCandidate("region-b", "지역 B", null),
				new RegionCandidate("region-c", "지역 C", null)
		));
	}

	private StubResponse okResponse(Object structuredOutput) {
		String outputText = json(structuredOutput);
		return new StubResponse(200, json(Map.of(
				"status", "completed",
				"output", List.of(Map.of(
						"type", "message",
						"content", List.of(Map.of("type", "output_text", "text", outputText))
				))
		)), Map.of());
	}

	private void handle(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		requests.add(new CapturedRequest(exchange.getRequestHeaders().getFirst("Authorization"), body));
		StubResponse response = responses.remove();
		if (response.delayMillis() > 0) {
			try {
				Thread.sleep(response.delayMillis());
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
		response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
		byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(response.status(), bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readMap(String json) {
		return (Map<String, Object>) objectMapper.readValue(json, Map.class);
	}

	private String json(Object value) {
		return objectMapper.writeValueAsString(value);
	}

	private void assertUnavailable(Runnable invocation) {
		assertThatThrownBy(invocation::run)
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
	}

	private void assertInvalid(Runnable invocation) {
		assertThatThrownBy(invocation::run)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID);
					assertThat(exception.getMessage()).doesNotContain("provider text", "max_output_tokens");
				});
	}

	private record StubResponse(int status, String body, Map<String, String> headers, long delayMillis) {

		private StubResponse(int status, String body, Map<String, String> headers) {
			this(status, body, headers, 0);
		}
	}

	private record CapturedRequest(String authorization, String body) {
	}
}
