package com.example.travel.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceImageApiRequest(
		@NotBlank @Size(max = 120) String placeName,
		@NotBlank @Size(max = 200) String address
) { }
