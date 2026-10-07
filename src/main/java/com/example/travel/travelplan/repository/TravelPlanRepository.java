package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.TravelPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {
    Optional<TravelPlan> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from TravelPlan plan where plan.id = ?1 and plan.userId = ?2")
    Optional<TravelPlan> findOwnedForUpdate(Long id, Long userId);
    List<TravelPlan> findByUserIdOrderByStartDateDesc(Long userId);
    List<TravelPlan> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<TravelPlan> findByUserId(Long userId);
}
