package com.example.travel.place.service;

import com.example.travel.place.client.KakaoImageClient;
import com.example.travel.place.dto.PlaceImageApiRequest;
import com.example.travel.user.dto.RequestExecutionLease;
import com.example.travel.user.dto.RequestStartResult;
import com.example.travel.user.dto.UsageFeature;
import com.example.travel.user.service.RequestExecutionService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceImageServiceTest {
	@Test
	void searchesWithPlaceNameAndRegionUnderItsOwnQuota() {
		KakaoImageClient client = mock(KakaoImageClient.class);
		RequestExecutionService executions = mock(RequestExecutionService.class);
		UUID requestId = UUID.randomUUID();
		RequestExecutionLease lease = new RequestExecutionLease(
				7L, UsageFeature.IMAGE_SEARCH, requestId, Instant.parse("2026-09-20T01:00:00Z"));
		when(executions.tryStart(7L, UsageFeature.IMAGE_SEARCH, requestId, 1))
				.thenReturn(RequestStartResult.started(lease));
		when(executions.markSucceeded(lease)).thenReturn(true);
		when(client.search("테스트 장소 테스트도 테스트시"))
				.thenReturn(List.of(URI.create("https://search2.kakaocdn.net/argon/example")));

		var result = new PlaceImageService(client, executions).search(7L, requestId,
				new PlaceImageApiRequest("테스트 장소", "테스트도 테스트시 도로 1"));

		assertThat(result.thumbnailUrls()).containsExactly(URI.create("https://search2.kakaocdn.net/argon/example"));
		verify(executions).markSucceeded(lease);
	}
}
