package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.PlanPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanPlaceRepository extends JpaRepository<PlanPlace, Long> {
    List<PlanPlace> findByTravelPlanId(Long travelPlanId);
    void deleteByTravelPlanId(Long travelPlanId);
}
