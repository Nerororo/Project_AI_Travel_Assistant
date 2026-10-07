package com.example.travel.travelplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "travel_plan_items")
public class TravelPlanItem {
    public enum Type { VISIT, MEAL, MOVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "travel_plan_day_id", nullable = false)
    private Long travelPlanDayId;
    @Column(name = "travel_plan_id", nullable = false)
    private Long travelPlanId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "travel_plan_day_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "travel_plan_id", referencedColumnName = "travel_plan_id", insertable = false, updatable = false)
    })
    private TravelPlanDay day;
    @Column(name = "item_order", nullable = false)
    private int itemOrder;
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private Type itemType;
    @Column(name = "plan_place_id")
    private Long planPlaceId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "plan_place_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "travel_plan_id", referencedColumnName = "travel_plan_id", insertable = false, updatable = false)
    })
    private PlanPlace place;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    protected TravelPlanItem() {}
    public TravelPlanItem(Long travelPlanDayId, Long travelPlanId, int itemOrder, Type itemType,
                          Long planPlaceId, LocalTime startTime, LocalTime endTime, Integer estimatedMinutes) {
        this.travelPlanDayId = travelPlanDayId;
        this.travelPlanId = travelPlanId;
        this.itemOrder = itemOrder;
        this.itemType = itemType;
        this.planPlaceId = planPlaceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.estimatedMinutes = estimatedMinutes;
    }
    public Long id() { return id; }
    public Long travelPlanDayId() { return travelPlanDayId; }
    public Long travelPlanId() { return travelPlanId; }
    public int itemOrder() { return itemOrder; }
    public Type itemType() { return itemType; }
    public Long planPlaceId() { return planPlaceId; }
    public LocalTime startTime() { return startTime; }
    public LocalTime endTime() { return endTime; }
    public Integer estimatedMinutes() { return estimatedMinutes; }
}
