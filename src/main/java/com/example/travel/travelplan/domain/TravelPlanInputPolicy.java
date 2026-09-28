package com.example.travel.travelplan.domain;

/**
 * Scalar input limits shared by itinerary estimation policies.
 */
public final class TravelPlanInputPolicy {

	public static final int MIN_STAY_MINUTES = 30;
	public static final int MAX_STAY_MINUTES = 480;
	public static final int STAY_UNIT_MINUTES = 10;
	public static final int DEFAULT_MEAL_TRAVEL_BUFFER_MINUTES = 15;
	public static final int MIN_MEAL_TRAVEL_BUFFER_MINUTES = 0;
	public static final int MAX_MEAL_TRAVEL_BUFFER_MINUTES = 60;

	private TravelPlanInputPolicy() {
	}

	public static int validateStayMinutes(int stayMinutes) {
		if (stayMinutes < MIN_STAY_MINUTES || stayMinutes > MAX_STAY_MINUTES) {
			throw new IllegalArgumentException("stayMinutes must be between 30 and 480");
		}
		if (stayMinutes % STAY_UNIT_MINUTES != 0) {
			throw new IllegalArgumentException("stayMinutes must be a multiple of 10");
		}
		return stayMinutes;
	}

	public static int validateMealTravelBufferMinutes(int minutes) {
		if (minutes < MIN_MEAL_TRAVEL_BUFFER_MINUTES || minutes > MAX_MEAL_TRAVEL_BUFFER_MINUTES) {
			throw new IllegalArgumentException("mealTravelBufferMinutes must be between 0 and 60");
		}
		return minutes;
	}
}
