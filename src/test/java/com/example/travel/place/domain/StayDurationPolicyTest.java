package com.example.travel.place.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StayDurationPolicyTest {

	@Test
	void convertsAttractionCategoriesToSuggestedMinutes() {
		assertThat(suggestedAttractionMinutes("여행 > 관광,명소 > 자연명소")).isEqualTo(90);
		assertThat(suggestedAttractionMinutes("문화,예술 > 박물관")).isEqualTo(120);
		assertThat(suggestedAttractionMinutes("문화,예술 > 전시관")).isEqualTo(120);
		assertThat(suggestedAttractionMinutes("여행 > 관광,명소 > 체험농장")).isEqualTo(180);
		assertThat(suggestedAttractionMinutes("여행 > 관광,명소 > 등산로")).isEqualTo(240);
		assertThat(suggestedAttractionMinutes("여행 > 관광,명소 > 테마파크")).isEqualTo(360);
	}

	@Test
	void usesTheGeneralDurationForUnknownOrBlankAttractionCategories() {
		assertThat(suggestedAttractionMinutes("기타 > 알 수 없음")).isEqualTo(90);
		assertThat(suggestedAttractionMinutes("   ")).isEqualTo(90);
	}

	@Test
	void appliesTheMostSpecificCategoryWhenKeywordsOverlap() {
		assertThat(suggestedAttractionMinutes("박물관 체험관")).isEqualTo(180);
		assertThat(suggestedAttractionMinutes("산악 체험 테마파크")).isEqualTo(360);
	}

	@Test
	void restaurantIsFixedAndHotelHasNoTourismStayDuration() {
		assertThat(StayDurationPolicy.suggestedMinutes(PlaceRole.RESTAURANT, null))
				.isEqualTo(OptionalInt.of(60));
		assertThat(StayDurationPolicy.suggestedMinutes(PlaceRole.HOTEL, null)).isEmpty();
	}

	@Test
	void acceptsAttractionAdjustmentsAtTenMinuteBoundaries() {
		assertThat(StayDurationPolicy.validateAttractionAdjustment(30)).isEqualTo(30);
		assertThat(StayDurationPolicy.validateAttractionAdjustment(90)).isEqualTo(90);
		assertThat(StayDurationPolicy.validateAttractionAdjustment(480)).isEqualTo(480);
	}

	@Test
	void rejectsOutOfRangeOrNonTenMinuteAttractionAdjustments() {
		assertThatThrownBy(() -> StayDurationPolicy.validateAttractionAdjustment(20))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> StayDurationPolicy.validateAttractionAdjustment(490))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> StayDurationPolicy.validateAttractionAdjustment(35))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void doesNotExposeTheInternalStayClassification() {
		assertThat(StayDurationPolicy.class.getDeclaredClasses())
				.allSatisfy(type -> assertThat(Modifier.isPrivate(type.getModifiers())).isTrue());
	}

	@Test
	void rejectsMissingAttractionCategoryAndMissingRole() {
		assertThatNullPointerException()
				.isThrownBy(() -> StayDurationPolicy.suggestedMinutes(PlaceRole.ATTRACTION, null));
		assertThatNullPointerException()
				.isThrownBy(() -> StayDurationPolicy.suggestedMinutes(null, "museum"));
	}

	private int suggestedAttractionMinutes(String providerCategory) {
		return StayDurationPolicy.suggestedMinutes(PlaceRole.ATTRACTION, providerCategory).orElseThrow();
	}
}
