package com.example.travel.travelplan.dto;

/** Request-local calculation input and result; never cached or persisted. */
public record ResolvedEstimate(EstimateCommand command, EstimateResult result) {
}
