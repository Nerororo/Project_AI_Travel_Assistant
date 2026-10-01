package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {
    Optional<TravelPlan> findByIdAndUserId(Long id, Long userId);
    List<TravelPlan> findByUserIdOrderByStartDateDesc(Long userId);
    List<TravelPlan> findByUserId(Long userId);
}
