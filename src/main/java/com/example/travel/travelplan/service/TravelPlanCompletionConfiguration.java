package com.example.travel.travelplan.service;

import com.example.travel.route.client.CarRouteClient;
import com.example.travel.route.client.PublicTransitRouteClient;
import com.example.travel.route.service.RouteQuotaService;
import com.example.travel.route.service.RouteService;
import com.example.travel.route.service.RouteVerificationService;
import com.example.travel.place.service.TravelBoundarySelectionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TravelPlanCompletionConfiguration {
    @Bean
    TravelPlanCompletionCalculationService completionCalculation(TravelPlanEstimateService estimates,
            TravelBoundarySelectionService boundaries, RouteQuotaService quotas,
            CarRouteClient cars, PublicTransitRouteClient transit) {
        return new TravelPlanCompletionCalculationService(estimates, boundaries,
                new RouteVerificationService(quotas, new RouteService(cars, transit)));
    }
}
