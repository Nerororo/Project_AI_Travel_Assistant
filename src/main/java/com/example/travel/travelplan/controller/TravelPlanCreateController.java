package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.TravelPlanCreateApiRequest;
import com.example.travel.travelplan.dto.TravelPlanCreateApiResponse;
import com.example.travel.travelplan.service.TravelPlanCreateApiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanCreateController {
    private final TravelPlanCreateApiService service;

    public TravelPlanCreateController(TravelPlanCreateApiService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TravelPlanCreateApiResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") UUID requestId,
            @Valid @RequestBody TravelPlanCreateApiRequest request) {
        TravelPlanCreateApiResponse result = service.create(user.userId(), requestId, request);
        return ResponseEntity.created(URI.create("/api/travel-plans/" + result.travelPlanId())).body(result);
    }
}
