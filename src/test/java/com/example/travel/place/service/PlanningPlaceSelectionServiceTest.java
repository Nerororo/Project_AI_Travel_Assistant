package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.dto.SelectionTokenPlace;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningPlaceSelectionServiceTest {

	private final SelectionTokenService tokenService = mock(SelectionTokenService.class);
	private final PlanningPlaceSelectionService service = new PlanningPlaceSelectionService(tokenService);

	@Test
	void attractionIsVerifiedWithFixedRoleAndMinimalResult() {
		when(tokenService.verify("attraction", 7L, "KR-CITY", PlaceRole.ATTRACTION))
				.thenReturn(place(PlaceRole.ATTRACTION));

		PlanningPlaceSelection selected = service.verifyAttraction("attraction", 7L, "KR-CITY");

		assertThat(selected.kakaoPlaceId()).isEqualTo("12345");
		assertThat(selected.placeUrl()).isEqualTo(URI.create("https://place.map.kakao.com/12345"));
		assertThat(selected.latitude()).isEqualTo(36.1);
		assertThat(selected.longitude()).isEqualTo(127.1);
		assertThat(PlanningPlaceSelection.class.getRecordComponents())
				.extracting(component -> component.getName())
				.containsExactly("kakaoPlaceId", "placeUrl", "latitude", "longitude");
		verify(tokenService).verify("attraction", 7L, "KR-CITY", PlaceRole.ATTRACTION);
	}

	@Test
	void hotelIsVerifiedWithFixedRole() {
		when(tokenService.verify("hotel", 7L, "KR-CITY", PlaceRole.HOTEL))
				.thenReturn(place(PlaceRole.HOTEL));

		assertThat(service.verifyHotel("hotel", 7L, "KR-CITY").kakaoPlaceId()).isEqualTo("12345");
		verify(tokenService).verify("hotel", 7L, "KR-CITY", PlaceRole.HOTEL);
	}

	@Test
	void wrongRoleOrExpiredTokenIsSafeValidationFailure() {
		when(tokenService.verify("wrong-role", 7L, "KR-CITY", PlaceRole.HOTEL))
				.thenThrow(new SelectionTokenService.InvalidSelectionTokenException());

		assertThatThrownBy(() -> service.verifyHotel("wrong-role", 7L, "KR-CITY"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(exception.details()).isNull();
				});
	}

	private static SelectionTokenPlace place(PlaceRole role) {
		return new SelectionTokenPlace(7L, "KR-CITY", role, "12345",
				URI.create("https://place.map.kakao.com/12345"), 36.1, 127.1);
	}
}
