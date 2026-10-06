package com.example.travel.travelplan.controller;

import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanPatchRequest;
import com.example.travel.travelplan.service.TravelPlanMutationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanMutationController {
    private final TravelPlanMutationService mutations;

    public TravelPlanMutationController(TravelPlanMutationService mutations) {
        this.mutations = mutations;
    }

    @PatchMapping("/{travelPlanId}")
    public TravelPlanDetailResponse patch(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long travelPlanId, @RequestBody Map<String, Object> body) {
        return mutations.patch(user.userId(), travelPlanId, TravelPlanPatchRequest.from(body));
    }

    @DeleteMapping("/{travelPlanId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long travelPlanId) {
        mutations.delete(user.userId(), travelPlanId);
        return ResponseEntity.noContent().build();
    }
}
