package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.SelectionTokenPlace;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelBoundarySelectionServiceTest {

	private final SelectionTokenService tokenService = mock(SelectionTokenService.class);
	private final TravelBoundarySelectionService service =
			new TravelBoundarySelectionService(tokenService);

	@Test
	void verifiesDedicatedRoleAndReturnsOnlyCalculationFields() {
		URI placeUrl = URI.create("https://place.map.kakao.com/12345");
		when(tokenService.verify("token", 7L, "KR-CITY", PlaceRole.TRAVEL_BOUNDARY))
				.thenReturn(new SelectionTokenPlace(
						7L, "KR-CITY", PlaceRole.TRAVEL_BOUNDARY,
						"12345", placeUrl, 36.1, 127.1));

		var selection = service.verify("token", 7L, "KR-CITY");

		assertThat(selection.kakaoPlaceId()).isEqualTo("12345");
		assertThat(selection.placeUrl()).isEqualTo(placeUrl);
		assertThat(selection.latitude()).isEqualTo(36.1);
		assertThat(selection.longitude()).isEqualTo(127.1);
		verify(tokenService).verify("token", 7L, "KR-CITY", PlaceRole.TRAVEL_BOUNDARY);
	}

	@Test
	void convertsInvalidTokenToSafeValidationFailure() {
		when(tokenService.verify("invalid", 7L, "KR-CITY", PlaceRole.TRAVEL_BOUNDARY))
				.thenThrow(new SelectionTokenService.InvalidSelectionTokenException());

		assertThatThrownBy(() -> service.verify("invalid", 7L, "KR-CITY"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.details()).isNull();
				});
	}
}
