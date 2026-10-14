package com.example.travel.place.service;

import com.example.travel.region.dto.PlaceSearchRegion;

final class PlaceAddressMatcher {

	private PlaceAddressMatcher() {
	}

	static boolean matches(String address, PlaceSearchRegion region) {
		PlaceSearchRegion.AddressBoundary boundary = region.addressBoundary();
		if (boundary == null || address == null) {
			return false;
		}
		String[] parts = address.trim().split("\\s+");
		if (!boundary.region1Names().contains(parts[0])) {
			return false;
		}
		return boundary.region2Names().isEmpty()
				|| (parts.length > 1 && boundary.region2Names().stream()
						.anyMatch(name -> parts[1].equals(name.split("\\s+")[0])));
	}
}
