package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.TravelPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelPlanItemRepository extends JpaRepository<TravelPlanItem, Long> {
    List<TravelPlanItem> findByTravelPlanDayIdOrderByItemOrder(Long travelPlanDayId);
    void deleteByTravelPlanId(Long travelPlanId);
}
