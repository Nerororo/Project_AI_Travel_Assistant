package com.example.travel.travelplan.repository;

import com.example.travel.travelplan.domain.TravelPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {
    Optional<TravelPlan> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from TravelPlan plan where plan.id = ?1 and plan.userId = ?2")
    Optional<TravelPlan> findOwnedForUpdate(Long id, Long userId);
    List<TravelPlan> findByUserIdOrderByStartDateDesc(Long userId);
    List<TravelPlan> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    Slice<TravelPlan> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    long countByUserId(Long userId);
    @Modifying
    @Query(value = "INSERT IGNORE INTO travel_plan_creation_locks (user_id) VALUES (?1)", nativeQuery = true)
    int ensureCreationLock(Long userId);
    @Query(value = "SELECT user_id FROM travel_plan_creation_locks WHERE user_id = ?1 FOR UPDATE", nativeQuery = true)
    Long lockForCreation(Long userId);
    List<TravelPlan> findByUserId(Long userId);
}
