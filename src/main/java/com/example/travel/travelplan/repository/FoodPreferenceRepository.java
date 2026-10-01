package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.FoodPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodPreferenceRepository extends JpaRepository<FoodPreference, Long> {
    List<FoodPreference> findByTravelPlanId(Long travelPlanId);
    void deleteByTravelPlanId(Long travelPlanId);
}
