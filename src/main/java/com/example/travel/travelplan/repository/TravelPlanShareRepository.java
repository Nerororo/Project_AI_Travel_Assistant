package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.TravelPlanShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TravelPlanShareRepository extends JpaRepository<TravelPlanShare, Long> {
    Optional<TravelPlanShare> findByTokenHash(byte[] tokenHash);
}
