package com.example.travel.travelplan.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelPlanInputPolicyTest {

	@Test
	void acceptsStayMinutesAtTenMinuteBoundaries() {
		assertThat(TravelPlanInputPolicy.validateStayMinutes(30)).isEqualTo(30);
		assertThat(TravelPlanInputPolicy.validateStayMinutes(480)).isEqualTo(480);
	}

	@Test
	void rejectsStayMinutesOutsideRangeOrUnit() {
		assertThatThrownBy(() -> TravelPlanInputPolicy.validateStayMinutes(20))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TravelPlanInputPolicy.validateStayMinutes(490))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TravelPlanInputPolicy.validateStayMinutes(35))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsMealTravelBufferFromZeroToSixtyMinutes() {
		assertThat(TravelPlanInputPolicy.DEFAULT_MEAL_TRAVEL_BUFFER_MINUTES).isEqualTo(15);
		assertThat(TravelPlanInputPolicy.validateMealTravelBufferMinutes(0)).isZero();
		assertThat(TravelPlanInputPolicy.validateMealTravelBufferMinutes(60)).isEqualTo(60);
	}

	@Test
	void rejectsMealTravelBufferOutsideRange() {
		assertThatThrownBy(() -> TravelPlanInputPolicy.validateMealTravelBufferMinutes(-1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TravelPlanInputPolicy.validateMealTravelBufferMinutes(61))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
