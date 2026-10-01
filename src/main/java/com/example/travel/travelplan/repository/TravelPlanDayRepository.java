package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.TravelPlanDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelPlanDayRepository extends JpaRepository<TravelPlanDay, Long> {
    List<TravelPlanDay> findByTravelPlanIdOrderByDayNumber(Long travelPlanId);
    void deleteByTravelPlanId(Long travelPlanId);
}
