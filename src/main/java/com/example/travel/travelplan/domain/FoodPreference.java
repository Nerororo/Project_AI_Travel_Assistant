package com.example.travel.travelplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_preferences")
public class FoodPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "travel_plan_id", nullable = false)
    private Long travelPlanId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", insertable = false, updatable = false)
    private TravelPlan travelPlan;
    @Column(name = "food_name", nullable = false, length = 50)
    private String foodName;

    protected FoodPreference() {}
    public FoodPreference(Long travelPlanId, String foodName) {
        this.travelPlanId = travelPlanId;
        this.foodName = foodName;
    }
    public Long id() { return id; }
    public Long travelPlanId() { return travelPlanId; }
    public String foodName() { return foodName; }
}
