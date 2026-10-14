package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.client.KakaoImageClient;
import com.example.travel.place.dto.PlaceImageApiRequest;
import com.example.travel.place.dto.PlaceImageApiResponse;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.RequestExecutionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PlaceImageService {
	private final KakaoImageClient client;
	private final RequestExecutionService executions;

	public PlaceImageService(KakaoImageClient client, RequestExecutionService executions) {
		this.client = client;
		this.executions = executions;
	}

	public PlaceImageApiResponse search(long userId, UUID requestId, PlaceImageApiRequest request) {
		RequestStartResult start = executions.tryStart(userId, UsageFeature.IMAGE_SEARCH, requestId, 1);
		if (!start.started()) {
			throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), start.retryAfterSeconds());
		}
		RequestExecutionLease lease = start.lease();
		try {
			String[] parts = request.address().trim().split("\\s+");
			String region = String.join(" ", List.of(parts).subList(0, Math.min(2, parts.length)));
			PlaceImageApiResponse response = new PlaceImageApiResponse(
					client.search(request.placeName().trim() + " " + region));
			if (!executions.markSucceeded(lease)) {
				throw new IllegalStateException("Request execution could not be completed");
			}
			return response;
		}
		catch (RuntimeException exception) {
			executions.releaseAfterFailure(lease);
			throw exception;
		}
	}
}
