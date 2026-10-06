package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanShareResponse;
import com.example.travel.travelplan.service.TravelPlanShareService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TravelPlanShareController {
    private final TravelPlanShareService service;

    public TravelPlanShareController(TravelPlanShareService service) {
        this.service = service;
    }

    @PostMapping("/api/travel-plans/{travelPlanId}/shares")
    public ResponseEntity<TravelPlanShareResponse> issue(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long travelPlanId) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.issue(user.userId(), travelPlanId));
    }

    @GetMapping("/api/shared/travel-plans/{shareToken}")
    public ResponseEntity<TravelPlanDetailResponse> read(@PathVariable String shareToken,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(service.read(shareToken));
    }
}
