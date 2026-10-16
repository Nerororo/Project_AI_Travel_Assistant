package com.example.travel.place.dto;

import java.net.URI;
import java.util.List;

public record PlaceImageApiResponse(List<URI> thumbnailUrls) { }
