package com.example.travel.ai.service;

import com.example.travel.ai.client.AiClient;
import com.example.travel.ai.client.FakeAiClient;
import com.example.travel.ai.dto.AttractionContext;
import com.example.travel.ai.dto.MenuAnalysisRequest;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MenuAnalysisServiceTest {

	private static final List<AttractionContext> ATTRACTIONS = List.of(
			new AttractionContext("place-a", "사용자 장소 A"),
			new AttractionContext("place-b", "사용자 장소 B")
	);

	private FakeAiClient client;
	private MenuAnalysisService service;

	@BeforeEach
	void setUp() {
		client = new FakeAiClient();
		service = new MenuAnalysisService(client);
	}

	@Test
	void returnsOneToFiveStructuredMenusWithOptionalAttractionTarget() {
		client.menuResult(new AiClient.MenuAnalysisResult(List.of(
				menu("메뉴 A", "검색어 A", "place-a"),
				menu("메뉴 B", "검색어 B", null)
		)));

		var response = service.analyze(new MenuAnalysisRequest(
				" region-a ",
				"  매운 음식  ",
				ATTRACTIONS
		));

		assertThat(client.lastMenuPrompt().regionId()).isEqualTo("region-a");
		assertThat(client.lastMenuPrompt().request()).isEqualTo("매운 음식");
		assertThat(client.lastMenuPrompt().attractions()).isEqualTo(ATTRACTIONS);
		assertThat(response.menus()).hasSize(2);
		assertThat(response.menus().get(0).targetClientPlaceId()).isEqualTo("place-a");
		assertThat(response.menus().get(1).targetClientPlaceId()).isNull();
	}

	@Test
	void rejectsEmptyAndMoreThanFiveMenus() {
		assertInvalid(new AiClient.MenuAnalysisResult(List.of()));

		List<AiClient.MenuSuggestion> sixMenus = new ArrayList<>();
		for (int index = 0; index < 6; index++) {
			sixMenus.add(menu("메뉴 " + index, "검색어 " + index, null));
		}
		assertInvalid(new AiClient.MenuAnalysisResult(sixMenus));
	}

	@Test
	void rejectsDuplicateMenusMissingFieldsAndUnknownAttractionTargets() {
		assertInvalid(new AiClient.MenuAnalysisResult(List.of(
				menu("메뉴 A", "검색어 A", null),
				menu(" 메뉴 A ", "검색어 B", null)
		)));
		assertInvalid(new AiClient.MenuAnalysisResult(List.of(
				menu("메뉴 A", " ", null)
		)));
		assertInvalid(new AiClient.MenuAnalysisResult(List.of(
				menu("메뉴 A", "검색어 A", "unknown-place")
		)));
	}

	@Test
	void rejectsNullMenuListAndNullMenuItem() {
		assertInvalid(new AiClient.MenuAnalysisResult(null));
		List<AiClient.MenuSuggestion> menus = new ArrayList<>();
		menus.add(null);
		assertInvalid(new AiClient.MenuAnalysisResult(menus));
	}

	private AiClient.MenuSuggestion menu(String name, String searchQuery, String targetClientPlaceId) {
		return new AiClient.MenuSuggestion(name, searchQuery, "안전한 분석 이유", targetClientPlaceId);
	}

	private void assertInvalid(AiClient.MenuAnalysisResult result) {
		client.menuResult(result);
		assertThatThrownBy(() -> service.analyze(new MenuAnalysisRequest(
				"region-a",
				"음식 요청",
				ATTRACTIONS
		)))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
	}
}
