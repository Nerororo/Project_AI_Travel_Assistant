package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanListItemResponse;
import com.example.travel.travelplan.service.TravelPlanReadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanReadController {
    private final TravelPlanReadService service;

    public TravelPlanReadController(TravelPlanReadService service) {
        this.service = service;
    }

    @GetMapping
    public List<TravelPlanListItemResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.userId());
    }

    @GetMapping("/{travelPlanId}")
    public TravelPlanDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long travelPlanId) {
        return service.detail(user.userId(), travelPlanId);
    }
}
