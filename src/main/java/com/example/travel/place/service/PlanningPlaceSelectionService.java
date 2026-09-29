package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.PlanningPlaceSelection;
import com.example.travel.place.dto.SelectionTokenPlace;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Public Place boundary for request-scoped attraction and hotel selections. */
@Service
public class PlanningPlaceSelectionService {

	private final SelectionTokenService selectionTokenService;

	public PlanningPlaceSelectionService(SelectionTokenService selectionTokenService) {
		this.selectionTokenService = Objects.requireNonNull(selectionTokenService);
	}

	public PlanningPlaceSelection verifyAttraction(String token, long userId, String regionId) {
		return verify(token, userId, regionId, PlaceRole.ATTRACTION);
	}

	public PlanningPlaceSelection verifyHotel(String token, long userId, String regionId) {
		return verify(token, userId, regionId, PlaceRole.HOTEL);
	}

	private PlanningPlaceSelection verify(String token, long userId, String regionId, PlaceRole role) {
		try {
			SelectionTokenPlace place = selectionTokenService.verify(token, userId, regionId, role);
			return new PlanningPlaceSelection(place.kakaoPlaceId(), place.placeUrl(),
					place.latitude(), place.longitude());
		} catch (SelectionTokenService.InvalidSelectionTokenException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED);
		}
	}
}
