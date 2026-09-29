package com.example.travel.place.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.place.dto.TravelBoundarySelection;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Public place-domain boundary for consuming a verified travel-boundary token.
 */
@Service
public class TravelBoundarySelectionService {

	private final SelectionTokenService selectionTokenService;

	public TravelBoundarySelectionService(SelectionTokenService selectionTokenService) {
		this.selectionTokenService = Objects.requireNonNull(
				selectionTokenService, "selectionTokenService must not be null");
	}

	public TravelBoundarySelection verify(
			String selectionToken,
			long authenticatedUserId,
			String regionId
	) {
		try {
			SelectionTokenPlace place = selectionTokenService.verify(
					selectionToken, authenticatedUserId, regionId, PlaceRole.TRAVEL_BOUNDARY);
			return new TravelBoundarySelection(
					place.kakaoPlaceId(), place.placeUrl(), place.latitude(), place.longitude());
		} catch (SelectionTokenService.InvalidSelectionTokenException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED);
		}
	}
}
