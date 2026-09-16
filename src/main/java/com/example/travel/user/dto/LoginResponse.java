package com.example.travel.user.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
