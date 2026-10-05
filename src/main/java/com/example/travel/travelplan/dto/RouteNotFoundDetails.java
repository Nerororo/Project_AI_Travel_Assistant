package com.example.travel.travelplan.dto;

import com.example.travel.global.exception.ErrorDetails;
import com.example.travel.route.algorithm.TravelMode;

import java.time.LocalDate;
import java.util.Objects;

/** Identifies one MOVE in the final completion candidate without exposing place data. */
public record RouteNotFoundDetails(LocalDate date, int moveOrder, TravelMode travelMode)
        implements ErrorDetails {

    public RouteNotFoundDetails {
        Objects.requireNonNull(date);
        if (moveOrder < 1) throw new IllegalArgumentException("moveOrder must be positive");
        Objects.requireNonNull(travelMode);
    }
}
