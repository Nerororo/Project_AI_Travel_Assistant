package com.example.travel.travelplan.dto;

import java.time.Instant;

public record TravelPlanShareResponse(String shareToken, Instant expiresAt) { }
