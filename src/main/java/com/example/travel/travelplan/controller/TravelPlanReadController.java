package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanListPageResponse;
import com.example.travel.travelplan.service.TravelPlanReadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanReadController {
    private final TravelPlanReadService service;

    public TravelPlanReadController(TravelPlanReadService service) {
        this.service = service;
    }

    @GetMapping
    public TravelPlanListPageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(user.userId(), page, size);
    }

    @GetMapping("/{travelPlanId}")
    public TravelPlanDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long travelPlanId) {
        return service.detail(user.userId(), travelPlanId);
    }
}
