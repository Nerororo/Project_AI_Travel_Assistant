package com.example.travel.region.domain;

import java.util.List;

public record AddressBoundary(List<String> region1Names, List<String> region2Names) {

	public AddressBoundary {
		region1Names = region1Names == null ? null : List.copyOf(region1Names);
		region2Names = region2Names == null ? null : List.copyOf(region2Names);
	}
}
